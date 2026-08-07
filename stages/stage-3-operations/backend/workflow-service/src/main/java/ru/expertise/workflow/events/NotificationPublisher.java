package ru.expertise.workflow.events;

import org.springframework.stereotype.Component;
import ru.expertise.workflow.config.WorkflowProperties;
import ru.expertise.workflow.domain.TaskInstance;

import java.util.Map;

/**
 * Emits notification intents for notification-service (TZ §5: key actions produce domain events and
 * notifications). This module only states who should be told about what — delivery, templates and
 * channels belong to notification-service, which is outside its boundary (TZ §2).
 */
@Component
public class NotificationPublisher {

    private final OutboxEventWriter outboxEventWriter;
    private final WorkflowProperties properties;

    public NotificationPublisher(OutboxEventWriter outboxEventWriter, WorkflowProperties properties) {
        this.outboxEventWriter = outboxEventWriter;
        this.properties = properties;
    }

    public void taskNotification(TaskInstance task, String kind, String correlationId) {
        String topic = properties.getKafka().getTopicNotifications();
        if (topic == null || topic.isBlank()) {
            return;
        }
        outboxEventWriter.enqueue("TaskInstance", task.getId().toString(), DomainEventType.NOTIFICATION_REQUESTED,
                topic, correlationId, task.getProcessInstance().getBusinessKey(),
                Map.of(
                        "kind", kind,
                        "taskInstanceId", task.getId(),
                        "processInstanceId", task.getProcessInstance().getId(),
                        "stepCode", task.getStepCode(),
                        "recipientRole", task.getAssigneeRole() == null ? "" : task.getAssigneeRole(),
                        "recipientUser", task.getAssigneeId() == null ? "" : task.getAssigneeId()));
    }
}
