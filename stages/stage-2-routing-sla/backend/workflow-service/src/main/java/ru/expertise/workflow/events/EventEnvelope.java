package ru.expertise.workflow.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Wire envelope published to Kafka: {eventId, eventType, version, occurredAt, correlationId, businessKey, payload}. */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String businessKey,
        JsonNode payload
) {
}
