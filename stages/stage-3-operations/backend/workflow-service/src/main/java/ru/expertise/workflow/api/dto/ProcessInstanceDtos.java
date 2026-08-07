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
            Map<String, Object> attributes,
            String correlationId
    ) {
    }

    public record SubProcessRequest(
            @NotNull UUID processDefinitionId,
            @NotBlank String businessKey,
            Map<String, Object> attributes,
            String correlationId
    ) {
    }

    public record TransitionRequest(
            @NotBlank String reason
    ) {
    }

    public record Response(
            UUID id,
            UUID processDefinitionId,
            int processVersion,
            String businessKey,
            String status,
            String currentStepCode,
            UUID parentInstanceId,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
