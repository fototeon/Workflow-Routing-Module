package ru.expertise.workflow.process;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.expertise.workflow.audit.AuditContext;
import ru.expertise.workflow.audit.Audited;
import ru.expertise.workflow.config.WorkflowProperties;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.SlaPolicy;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.domain.TaskInstanceStatus;
import ru.expertise.workflow.domain.TaskReassignment;
import ru.expertise.workflow.events.DomainEventType;
import ru.expertise.workflow.events.NotificationPublisher;
import ru.expertise.workflow.events.OutboxEventWriter;
import ru.expertise.workflow.repository.TaskInstanceRepository;
import ru.expertise.workflow.repository.TaskReassignmentRepository;
import ru.expertise.workflow.routing.RoutingDecision;
import ru.expertise.workflow.security.AccessPolicy;
import ru.expertise.workflow.sla.SlaDueDateCalculator;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskInstanceService {

    private final TaskInstanceRepository taskInstanceRepository;
    private final TaskReassignmentRepository taskReassignmentRepository;
    private final SlaDueDateCalculator slaDueDateCalculator;
    private final OutboxEventWriter outboxEventWriter;
    private final NotificationPublisher notificationPublisher;
    private final AccessPolicy accessPolicy;
    private final WorkflowProperties properties;
    /** Self-proxy so @Audited/@Transactional still apply when this service calls its own methods. */
    private final TaskInstanceService self;

    public TaskInstanceService(TaskInstanceRepository taskInstanceRepository,
                                TaskReassignmentRepository taskReassignmentRepository,
                                SlaDueDateCalculator slaDueDateCalculator,
                                OutboxEventWriter outboxEventWriter,
                                NotificationPublisher notificationPublisher,
                                AccessPolicy accessPolicy,
                                WorkflowProperties properties,
                                @Lazy TaskInstanceService self) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.taskReassignmentRepository = taskReassignmentRepository;
        this.slaDueDateCalculator = slaDueDateCalculator;
        this.outboxEventWriter = outboxEventWriter;
        this.notificationPublisher = notificationPublisher;
        this.accessPolicy = accessPolicy;
        this.properties = properties;
        this.self = self;
    }

    @Transactional
    @Audited(DomainEventType.TASK_CREATED)
    public TaskInstance createTask(ProcessInstance processInstance, RoutingDecision decision, SlaPolicy slaPolicy, String correlationId) {
        TaskInstance task = new TaskInstance();
        task.setProcessInstance(processInstance);
        task.setStepCode(decision.targetStepCode());
        task.setName(decision.targetStepCode());
        task.setAssigneeRole(decision.targetRole());
        task.setStatus(TaskInstanceStatus.CREATED);
        if (slaPolicy != null) {
            task.setDueAt(slaDueDateCalculator.computeDueAt(slaPolicy, Instant.now()));
        }
        task = taskInstanceRepository.save(task);

        AuditContext.processInstanceId(processInstance.getId());
        AuditContext.taskInstanceId(task.getId());
        AuditContext.correlationId(correlationId);
        AuditContext.detail("stepCode", task.getStepCode());
        AuditContext.detail("assigneeRole", task.getAssigneeRole());
        AuditContext.detail("dueAt", task.getDueAt());

        outboxEventWriter.enqueue("TaskInstance", task.getId().toString(), DomainEventType.TASK_CREATED,
                properties.getKafka().getTopicTaskEvents(), correlationId, processInstance.getBusinessKey(),
                Map.of(
                        "taskInstanceId", task.getId(),
                        "processInstanceId", processInstance.getId(),
                        "stepCode", task.getStepCode(),
                        "assigneeRole", task.getAssigneeRole() == null ? "" : task.getAssigneeRole()
                ));
        notificationPublisher.taskNotification(task, "TASK_ASSIGNED", correlationId);

        return task;
    }

    @Transactional
    @Audited(DomainEventType.TASK_COMPLETED)
    public TaskInstance completeTask(UUID taskId, String actorId) {
        TaskInstance task = get(taskId);
        if (task.getStatus() == TaskInstanceStatus.CREATED) {
            // Completing an unclaimed task implicitly takes it into work first: CREATED -> COMPLETED
            // is not a legal transition on its own (see TaskInstanceStatus), and there is no separate
            // "take into work" action in the API/UI.
            assertTransitionAllowed(task.getStatus(), TaskInstanceStatus.IN_PROGRESS);
            task.setStatus(TaskInstanceStatus.IN_PROGRESS);
        }
        assertTransitionAllowed(task.getStatus(), TaskInstanceStatus.COMPLETED);

        task.setStatus(TaskInstanceStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task = taskInstanceRepository.save(task);

        AuditContext.processInstanceId(task.getProcessInstance().getId());
        AuditContext.taskInstanceId(task.getId());
        AuditContext.detail("actorId", actorId);
        AuditContext.detail("stepCode", task.getStepCode());

        outboxEventWriter.enqueue("TaskInstance", task.getId().toString(), DomainEventType.TASK_COMPLETED,
                properties.getKafka().getTopicTaskEvents(), null, task.getProcessInstance().getBusinessKey(),
                Map.of("taskInstanceId", task.getId(), "processInstanceId", task.getProcessInstance().getId()));

        return task;
    }

    @Transactional
    @Audited(DomainEventType.TASK_REASSIGNED)
    public TaskInstance reassignTask(UUID taskId, String toAssignee, String reason, String actorId) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reassignment reason is required");
        }
        TaskInstance task = get(taskId);
        if (task.getStatus() == TaskInstanceStatus.COMPLETED || task.getStatus() == TaskInstanceStatus.CANCELLED) {
            throw new InvalidStatusTransitionException(task.getStatus().name(), "REASSIGNED");
        }

        TaskReassignment reassignment = new TaskReassignment();
        reassignment.setTaskInstance(task);
        reassignment.setFromAssignee(task.getAssigneeId());
        reassignment.setToAssignee(toAssignee);
        reassignment.setReason(reason);
        reassignment.setActorId(actorId);
        taskReassignmentRepository.save(reassignment);

        task.setAssigneeId(toAssignee);
        task = taskInstanceRepository.save(task);

        AuditContext.processInstanceId(task.getProcessInstance().getId());
        AuditContext.taskInstanceId(task.getId());
        AuditContext.detail("toAssignee", toAssignee);
        AuditContext.detail("reason", reason);

        outboxEventWriter.enqueue("TaskInstance", task.getId().toString(), DomainEventType.TASK_REASSIGNED,
                properties.getKafka().getTopicTaskEvents(), null, task.getProcessInstance().getBusinessKey(),
                Map.of("taskInstanceId", task.getId(), "toAssignee", toAssignee, "reason", reason));
        notificationPublisher.taskNotification(task, "TASK_REASSIGNED", null);

        return task;
    }

    @Transactional
    @Audited(DomainEventType.TASK_CANCELLED)
    public TaskInstance cancelTask(UUID taskId, String reason, String actorId) {
        TaskInstance task = get(taskId);
        assertTransitionAllowed(task.getStatus(), TaskInstanceStatus.CANCELLED);

        task.setStatus(TaskInstanceStatus.CANCELLED);
        task = taskInstanceRepository.save(task);

        AuditContext.processInstanceId(task.getProcessInstance().getId());
        AuditContext.taskInstanceId(task.getId());
        AuditContext.detail("actorId", actorId);
        AuditContext.detail("reason", reason);
        AuditContext.detail("stepCode", task.getStepCode());

        outboxEventWriter.enqueue("TaskInstance", task.getId().toString(), DomainEventType.TASK_CANCELLED,
                properties.getKafka().getTopicTaskEvents(), null, task.getProcessInstance().getBusinessKey(),
                Map.of("taskInstanceId", task.getId(), "processInstanceId", task.getProcessInstance().getId(),
                        "reason", reason));

        return task;
    }

    /** Cancels every still-open task of a process — used when the process itself is cancelled (TZ §5). */
    @Transactional
    public void cancelOpenTasks(UUID processInstanceId, String reason, String actorId) {
        for (TaskInstance task : taskInstanceRepository.findByProcessInstanceId(processInstanceId)) {
            if (task.getStatus().canTransitionTo(TaskInstanceStatus.CANCELLED)) {
                self.cancelTask(task.getId(), reason, actorId);
            }
        }
    }

    /** Stops the SLA clock of the process's open tasks while it is suspended (TZ §2 "паузы"). */
    @Transactional
    public void pauseSla(UUID processInstanceId, Instant pausedAt) {
        for (TaskInstance task : taskInstanceRepository.findByProcessInstanceId(processInstanceId)) {
            if (task.getDueAt() != null && task.getSlaPausedAt() == null
                    && task.getStatus() != TaskInstanceStatus.COMPLETED && task.getStatus() != TaskInstanceStatus.CANCELLED) {
                task.setSlaPausedAt(pausedAt);
                taskInstanceRepository.save(task);
            }
        }
    }

    /** Restarts the SLA clock, moving each deadline forward by however long the pause lasted. */
    @Transactional
    public void resumeSla(UUID processInstanceId, Instant resumedAt) {
        for (TaskInstance task : taskInstanceRepository.findByProcessInstanceId(processInstanceId)) {
            if (task.getSlaPausedAt() == null) {
                continue;
            }
            Duration pause = Duration.between(task.getSlaPausedAt(), resumedAt);
            if (task.getDueAt() != null && !pause.isNegative()) {
                task.setDueAt(task.getDueAt().plus(pause));
            }
            task.setSlaPausedAt(null);
            taskInstanceRepository.save(task);
        }
    }

    @Transactional(readOnly = true)
    public TaskInstance get(UUID taskId) {
        return taskInstanceRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("TaskInstance " + taskId + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<TaskInstance> search(Specification<TaskInstance> spec, Pageable pageable) {
        return taskInstanceRepository.findAll(spec.and(accessPolicy.visibleTasks()), pageable);
    }

    /** Rows for the CSV export (TZ §9), flattened inside the transaction. */
    @Transactional(readOnly = true)
    public java.util.List<java.util.List<String>> exportRows(Specification<TaskInstance> spec) {
        return taskInstanceRepository.findAll(spec.and(accessPolicy.visibleTasks())).stream()
                .map(task -> java.util.List.of(
                        task.getProcessInstance().getBusinessKey(),
                        task.getStepCode(),
                        task.getStatus().name(),
                        task.getAssigneeId() == null ? "" : task.getAssigneeId(),
                        task.getAssigneeRole() == null ? "" : task.getAssigneeRole(),
                        task.getDueAt() == null ? "" : task.getDueAt().toString(),
                        task.getCompletedAt() == null ? "" : task.getCompletedAt().toString()))
                .toList();
    }

    private void assertTransitionAllowed(TaskInstanceStatus from, TaskInstanceStatus to) {
        if (!from.canTransitionTo(to)) {
            throw new InvalidStatusTransitionException(from.name(), to.name());
        }
    }
}
