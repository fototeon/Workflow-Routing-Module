package ru.expertise.workflow.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract check for the published event schema (TZ §8 and the acceptance criterion in §12):
 * consumers depend on these field and type names, so a rename has to be a deliberate, visible change.
 */
class EventEnvelopeContractTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @Test
    void envelopeKeepsItsPublishedFieldNames() throws Exception {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), "TaskCreated", 1, Instant.now(),
                "corr-1", "REQ-1", objectMapper.readTree("{\"taskInstanceId\":\"x\"}"));

        var json = objectMapper.readTree(objectMapper.writeValueAsString(envelope));

        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "eventId", "eventType", "version", "occurredAt", "correlationId", "businessKey", "payload");
        assertThat(json.get("version").asInt()).isEqualTo(1);
        assertThat(json.get("eventType").asText()).isEqualTo("TaskCreated");
    }

    @Test
    void eventTypesRequiredByTheSpecificationKeepTheirWireNames() {
        Set<String> published = Stream.of(DomainEventType.values())
                .map(DomainEventType::wireName)
                .collect(Collectors.toUnmodifiableSet());

        // The four names spelled out in REQ-02-010 plus the ones consumers already rely on.
        assertThat(published).containsAll(List.of(
                "TaskCreated", "TaskCompleted", "SlaBreached", "ProcessStateChanged",
                "ProcessStarted", "TaskReassigned", "TaskCancelled", "SlaEscalated",
                "ProcessSuspended", "ProcessResumed", "NotificationRequested"));
    }

    @Test
    void wireNamesAreUniqueAcrossEventTypes() {
        long distinct = Stream.of(DomainEventType.values()).map(DomainEventType::wireName).distinct().count();
        assertThat(distinct).isEqualTo(DomainEventType.values().length);
    }
}
