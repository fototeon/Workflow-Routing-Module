package ru.expertise.workflow.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.expertise.workflow.domain.OutboxEvent;
import ru.expertise.workflow.repository.OutboxEventRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes a row to the transactional outbox. Must be called within the same DB transaction as the
 * domain state change it describes, so the write commits or rolls back atomically with it. Actual
 * Kafka publication happens asynchronously via {@code OutboxPublisher}.
 */
@Component
public class OutboxEventWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void enqueue(String aggregateType, String aggregateId, DomainEventType type, String topic,
                         String correlationId, String businessKey, Object payload) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), type.wireName(), 1, Instant.now(), correlationId, businessKey,
                objectMapper.valueToTree(payload));

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(type.wireName());
        event.setTopic(topic);
        event.setCorrelationId(correlationId);
        event.setPayload(objectMapper.valueToTree(envelope));
        repository.save(event);
    }
}
