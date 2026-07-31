package ru.expertise.workflow.process;

import jakarta.persistence.EntityNotFoundException;
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
import ru.expertise.workflow.events.OutboxEventWriter;
import ru.expertise.workflow.repository.TaskInstanceRepository;
import ru.expertise.workflow.repository.TaskReassignmentRepository;
import ru.expertise.workflow.routing.RoutingDecision;
import ru.expertise.workflow.sla.SlaDueDateCalculator;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskInstanceService {

    private final TaskInstanceRepository taskInstanceRepository;
    private final TaskReassignmentRepository taskReassignmentRepository;
    private final SlaDueDateCalculator slaDueDateCalculator;
    private final OutboxEventWriter outboxEventWriter;
    private final WorkflowProperties properties;

    public TaskInstanceService(TaskInstanceRepository taskInstanceRepository,
                                TaskReassignmentRepository taskReassignmentRepository,
                                SlaDueDateCalculator slaDueDateCalculator,
                                OutboxEventWriter outboxEventWriter,
                                WorkflowProperties properties) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.taskReassignmentRepository = taskReassignmentRepository;
        this.slaDueDateCalculator = slaDueDateCalculator;
        this.outboxEventWriter = outboxEventWriter;
        this.properties = properties;
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

        return task;
    }

    @Transactional
    @Audited(DomainEventType.TASK_COMPLETED)
    public TaskInstance completeTask(UUID taskId, String actorId) {
        TaskInstance task = get(taskId);
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

        return task;
    }

    @Transactional(readOnly = true)
    public TaskInstance get(UUID taskId) {
        return taskInstanceRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("TaskInstance " + taskId + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<TaskInstance> search(Specification<TaskInstance> spec, Pageable pageable) {
        return taskInstanceRepository.findAll(spec, pageable);
    }

    private void assertTransitionAllowed(TaskInstanceStatus from, TaskInstanceStatus to) {
        if (!from.canTransitionTo(to)) {
            throw new InvalidStatusTransitionException(from.name(), to.name());
        }
    }
}
