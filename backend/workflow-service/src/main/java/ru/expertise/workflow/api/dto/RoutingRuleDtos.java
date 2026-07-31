package ru.expertise.workflow.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public final class RoutingRuleDtos {

    private RoutingRuleDtos() {
    }

    public record Request(
            @NotBlank String name,
            int priority,
            @NotNull JsonNode conditionTree,
            @NotBlank String targetStepCode,
            String targetRole
    ) {
    }

    public record Response(
            UUID id,
            UUID processDefinitionId,
            String name,
            int priority,
            JsonNode conditionTree,
            String targetStepCode,
            String targetRole
    ) {
    }
}
