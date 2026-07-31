package ru.expertise.workflow.events;

/** Wire-level event type names used both in the process journal and the Kafka event envelope. */
public enum DomainEventType {
    PROCESS_STARTED("ProcessStarted"),
    PROCESS_STATE_CHANGED("ProcessStateChanged"),
    TASK_CREATED("TaskCreated"),
    TASK_COMPLETED("TaskCompleted"),
    TASK_REASSIGNED("TaskReassigned"),
    SLA_BREACHED("SlaBreached"),
    SLA_ESCALATED("SlaEscalated");

    private final String wireName;

    DomainEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
