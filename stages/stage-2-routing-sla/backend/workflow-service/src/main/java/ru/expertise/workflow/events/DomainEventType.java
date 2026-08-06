package ru.expertise.workflow.events;

/** Wire-level event type names used both in the process journal and the Kafka event envelope. */
public enum DomainEventType {
    PROCESS_STARTED("ProcessStarted"),
    PROCESS_STATE_CHANGED("ProcessStateChanged"),
    TASK_CREATED("TaskCreated"),
    TASK_COMPLETED("TaskCompleted"),
    TASK_REASSIGNED("TaskReassigned"),
    TASK_CANCELLED("TaskCancelled"),
    SLA_BREACHED("SlaBreached"),
    SLA_ESCALATED("SlaEscalated"),
    PROCESS_SUSPENDED("ProcessSuspended"),
    PROCESS_RESUMED("ProcessResumed"),
    DEFINITION_CREATED("ProcessDefinitionCreated"),
    DEFINITION_UPDATED("ProcessDefinitionUpdated"),
    DEFINITION_PUBLISHED("ProcessDefinitionPublished"),
    DEFINITION_ARCHIVED("ProcessDefinitionArchived"),
    ROUTING_RULE_ADDED("RoutingRuleAdded"),
    ROUTING_RULE_REMOVED("RoutingRuleRemoved"),
    SLA_POLICY_CREATED("SlaPolicyCreated"),
    SLA_POLICY_UPDATED("SlaPolicyUpdated"),
    NOTIFICATION_REQUESTED("NotificationRequested");

    private final String wireName;

    DomainEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
