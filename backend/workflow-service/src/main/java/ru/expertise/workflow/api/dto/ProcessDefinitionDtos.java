package ru.expertise.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public final class ProcessDefinitionDtos {

    private ProcessDefinitionDtos() {
    }

    public record Request(
            @NotBlank String code,
            @NotBlank String name,
            UUID slaPolicyId
    ) {
    }

    public record Response(
            UUID id,
            String code,
            String name,
            int version,
            String status,
            UUID slaPolicyId,
            /** How many routing rules the template carries; a draft with zero cannot be published. */
            long routingRuleCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
