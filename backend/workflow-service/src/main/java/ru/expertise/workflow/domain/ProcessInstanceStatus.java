package ru.expertise.workflow.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Statuses per TZ-02-WORKFLOW section 5: Не запущен, Выполняется, Ожидает внешнего действия,
 * Приостановлен, Просрочен, Завершен, Отменен.
 */
public enum ProcessInstanceStatus {
    NOT_STARTED,
    RUNNING,
    WAITING_EXTERNAL,
    SUSPENDED,
    OVERDUE,
    COMPLETED,
    CANCELLED;

    private static final Map<ProcessInstanceStatus, Set<ProcessInstanceStatus>> ALLOWED_TRANSITIONS = Map.of(
            NOT_STARTED, EnumSet.of(RUNNING, CANCELLED),
            RUNNING, EnumSet.of(WAITING_EXTERNAL, SUSPENDED, OVERDUE, COMPLETED, CANCELLED),
            WAITING_EXTERNAL, EnumSet.of(RUNNING, SUSPENDED, OVERDUE, CANCELLED),
            SUSPENDED, EnumSet.of(RUNNING, CANCELLED),
            OVERDUE, EnumSet.of(RUNNING, COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(ProcessInstanceStatus.class),
            CANCELLED, EnumSet.noneOf(ProcessInstanceStatus.class)
    );

    public boolean canTransitionTo(ProcessInstanceStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
