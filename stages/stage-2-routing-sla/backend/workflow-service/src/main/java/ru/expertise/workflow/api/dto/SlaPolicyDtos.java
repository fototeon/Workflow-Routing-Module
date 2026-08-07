package ru.expertise.workflow.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public final class SlaPolicyDtos {

    private SlaPolicyDtos() {
    }

    public record Request(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull @Min(1) Integer durationMinutes,
            boolean businessHoursOnly,
            @NotNull JsonNode escalationRules
    ) {
    }

    public record Response(
            UUID id,
            String code,
            String name,
            int durationMinutes,
            boolean businessHoursOnly,
            JsonNode escalationRules
    ) {
    }
}
