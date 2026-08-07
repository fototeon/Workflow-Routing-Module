package ru.expertise.workflow.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public final class ProcessEventLogDtos {

    private ProcessEventLogDtos() {
    }

    public record Response(
            UUID id,
            UUID processInstanceId,
            UUID taskInstanceId,
            String eventType,
            JsonNode payload,
            String correlationId,
            String actorId,
            Instant occurredAt
    ) {
    }
}
