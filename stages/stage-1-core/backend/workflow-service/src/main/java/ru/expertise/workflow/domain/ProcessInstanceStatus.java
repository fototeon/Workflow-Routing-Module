package ru.expertise.workflow.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Process statuses of TZ-02-WORKFLOW §5. This stage implements the part of the lifecycle that a
 * process reaches on its own: it starts, runs, and either finishes or is cancelled. The waiting,
 * suspended and overdue states arrive with SLA handling and pauses.
 */
public enum ProcessInstanceStatus {
    NOT_STARTED,
    RUNNING,
    COMPLETED,
    CANCELLED;

    private static final Map<ProcessInstanceStatus, Set<ProcessInstanceStatus>> ALLOWED_TRANSITIONS = Map.of(
            NOT_STARTED, EnumSet.of(RUNNING, CANCELLED),
            RUNNING, EnumSet.of(COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(ProcessInstanceStatus.class),
            CANCELLED, EnumSet.noneOf(ProcessInstanceStatus.class)
    );

    public boolean canTransitionTo(ProcessInstanceStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
