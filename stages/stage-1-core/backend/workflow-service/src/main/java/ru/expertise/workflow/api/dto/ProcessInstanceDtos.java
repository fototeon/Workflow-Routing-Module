package ru.expertise.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ProcessInstanceDtos {

    private ProcessInstanceDtos() {
    }

    public record StartRequest(
            @NotNull UUID processDefinitionId,
            @NotBlank String businessKey,
            /** Free-form attributes of the case; the routing rules are evaluated against them. */
            Map<String, Object> attributes
    ) {
    }

    public record Response(
            UUID id,
            UUID processDefinitionId,
            int processVersion,
            String businessKey,
            String status,
            String currentStepCode,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
