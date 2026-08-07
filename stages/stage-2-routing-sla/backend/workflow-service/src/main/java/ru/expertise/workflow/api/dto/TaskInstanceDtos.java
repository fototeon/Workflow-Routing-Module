package ru.expertise.workflow.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class TaskInstanceDtos {

    private TaskInstanceDtos() {
    }

    public record CompleteRequest(
            Map<String, Object> outcomeAttributes,
            String correlationId
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
            Instant dueAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
