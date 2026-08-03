package ru.expertise.workflow.sla;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.expertise.workflow.audit.AuditContext;
import ru.expertise.workflow.audit.Audited;
import ru.expertise.workflow.config.WorkflowProperties;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.domain.TaskInstanceStatus;
import ru.expertise.workflow.events.DomainEventType;
import ru.expertise.workflow.events.NotificationPublisher;
import ru.expertise.workflow.events.OutboxEventWriter;
import ru.expertise.workflow.process.InvalidStatusTransitionException;
import ru.expertise.workflow.repository.TaskInstanceRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Mutates a single {@link TaskInstance} in response to an SLA breach or escalation threshold,
 * each in its own transaction with its own audit row — called by {@link SlaEscalationScheduler}
 * per task so one failure doesn't roll back the whole scan.
 */
@Service
public class TaskSlaProcessor {

    private final TaskInstanceRepository taskInstanceRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final NotificationPublisher notificationPublisher;
    private final WorkflowProperties properties;

    public TaskSlaProcessor(TaskInstanceRepository taskInstanceRepository,
                             OutboxEventWriter outboxEventWriter,
                             NotificationPublisher notificationPublisher,
                             WorkflowProperties properties) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.notificationPublisher = notificationPublisher;
        this.properties = properties;
    }

    @Transactional
    @Audited(DomainEventType.SLA_BREACHED)
    public void markOverdue(UUID taskId) {
        TaskInstance task = getTask(taskId);
        if (!task.getStatus().canTransitionTo(TaskInstanceStatus.OVERDUE)) {
            throw new InvalidStatusTransitionException(task.getStatus().name(), TaskInstanceStatus.OVERDUE.name());
        }
        task.setStatus(TaskInstanceStatus.OVERDUE);
        taskInstanceRepository.save(task);

        AuditContext.processInstanceId(task.getProcessInstance().getId());
        AuditContext.taskInstanceId(task.getId());
        AuditContext.detail("dueAt", task.getDueAt());
        AuditContext.detail("stepCode", task.getStepCode());

        outboxEventWriter.enqueue("TaskInstance", task.getId().toString(), DomainEventType.SLA_BREACHED,
                properties.getKafka().getTopicSlaEvents(), null, task.getProcessInstance().getBusinessKey(),
                Map.of("taskInstanceId", task.getId(), "processInstanceId", task.getProcessInstance().getId(),
                        "dueAt", task.getDueAt().toString()));
        notificationPublisher.taskNotification(task, "SLA_BREACHED", null);
    }

    @Transactional
    @Audited(DomainEventType.SLA_ESCALATED)
    public void escalate(UUID taskId, EscalationRule rule) {
        TaskInstance task = getTask(taskId);
        task.setAssigneeRole(rule.escalateToRole());
        task.setLastEscalatedPercent(rule.afterPercent());
        taskInstanceRepository.save(task);

        AuditContext.processInstanceId(task.getProcessInstance().getId());
        AuditContext.taskInstanceId(task.getId());
        AuditContext.detail("afterPercent", rule.afterPercent());
        AuditContext.detail("escalateToRole", rule.escalateToRole());

        outboxEventWriter.enqueue("TaskInstance", task.getId().toString(), DomainEventType.SLA_ESCALATED,
                properties.getKafka().getTopicSlaEvents(), null, task.getProcessInstance().getBusinessKey(),
                Map.of("taskInstanceId", task.getId(), "afterPercent", rule.afterPercent(),
                        "escalateToRole", rule.escalateToRole()));
        notificationPublisher.taskNotification(task, "SLA_ESCALATED", null);
    }

    private TaskInstance getTask(UUID taskId) {
        return taskInstanceRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("TaskInstance " + taskId + " not found"));
    }
}
