package ru.expertise.workflow.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Statuses per TZ-02-WORKFLOW §5. Suspension arrives with the pause feature; the rest of the
 * lifecycle — including the overdue state the SLA scheduler drives — is here.
 */
public enum ProcessInstanceStatus {
    NOT_STARTED,
    RUNNING,
    WAITING_EXTERNAL,
    OVERDUE,
    COMPLETED,
    CANCELLED;

    private static final Map<ProcessInstanceStatus, Set<ProcessInstanceStatus>> ALLOWED_TRANSITIONS = Map.of(
            NOT_STARTED, EnumSet.of(RUNNING, CANCELLED),
            RUNNING, EnumSet.of(WAITING_EXTERNAL, OVERDUE, COMPLETED, CANCELLED),
            WAITING_EXTERNAL, EnumSet.of(RUNNING, OVERDUE, CANCELLED),
            OVERDUE, EnumSet.of(RUNNING, COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(ProcessInstanceStatus.class),
            CANCELLED, EnumSet.noneOf(ProcessInstanceStatus.class)
    );

    public boolean canTransitionTo(ProcessInstanceStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
