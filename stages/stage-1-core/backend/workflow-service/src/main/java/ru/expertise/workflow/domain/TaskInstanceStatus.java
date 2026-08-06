package ru.expertise.workflow.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum TaskInstanceStatus {
    CREATED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    private static final Map<TaskInstanceStatus, Set<TaskInstanceStatus>> ALLOWED_TRANSITIONS = Map.of(
            CREATED, EnumSet.of(IN_PROGRESS, CANCELLED),
            IN_PROGRESS, EnumSet.of(COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(TaskInstanceStatus.class),
            CANCELLED, EnumSet.noneOf(TaskInstanceStatus.class)
    );

    public boolean canTransitionTo(TaskInstanceStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
