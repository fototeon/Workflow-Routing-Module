package ru.expertise.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;

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

    /** Exactly one of {@code toAssignee} and {@code toRole} must be set; the reason is always required. */
    public record ReassignRequest(
            String toAssignee,
            String toRole,
            @NotBlank String reason
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
