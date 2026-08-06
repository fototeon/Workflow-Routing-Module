package ru.expertise.workflow.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class TaskInstanceDtos {

    private TaskInstanceDtos() {
    }

    public record CompleteRequest(
            /** Attributes describing the outcome; they decide which rule routes the process next. */
            Map<String, Object> outcomeAttributes
    ) {
    }

    public record Response(
            UUID id,
            UUID processInstanceId,
            String stepCode,
            String name,
            String assigneeId,
            String assigneeRole,
            String status,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
