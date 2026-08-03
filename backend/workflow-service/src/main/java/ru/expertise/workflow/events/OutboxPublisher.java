package ru.expertise.workflow.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.expertise.workflow.config.WorkflowProperties;
import ru.expertise.workflow.domain.OutboxEvent;
import ru.expertise.workflow.repository.OutboxEventRepository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the transactional outbox and publishes pending rows to Kafka. Rows that keep failing past
 * {@code workflow.outbox.max-attempts} are routed to the DLQ topic instead of retried forever.
 * Each pass claims its batch with a row lock and {@code SKIP LOCKED}, so running several replicas
 * does not publish the same event twice (TZ §11).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int SEND_TIMEOUT_SECONDS = 5;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final WorkflowProperties properties;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                            KafkaTemplate<Object, Object> kafkaTemplate,
                            WorkflowProperties properties) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${workflow.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.lockUnpublished(
                PageRequest.of(0, properties.getOutbox().getBatchSize()));

        for (OutboxEvent event : pending) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.setPublishedAt(Instant.now());
            event.setLastError(null);
        } catch (Exception e) {
            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(truncate(e.getMessage()));
            log.warn("Failed to publish outbox event {} (attempt {}): {}", event.getId(), event.getAttempts(), e.getMessage());
            if (event.getAttempts() >= properties.getOutbox().getMaxAttempts()) {
                routeToDlq(event, e);
            }
        }
        outboxEventRepository.save(event);
    }

    private void routeToDlq(OutboxEvent event, Exception cause) {
        try {
            kafkaTemplate.send(properties.getKafka().getTopicDlq(), event.getAggregateId(), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.setPublishedAt(Instant.now());
            event.setLastError(truncate("Routed to DLQ after " + event.getAttempts() + " attempts: " + cause.getMessage()));
        } catch (Exception dlqEx) {
            event.setLastError(truncate("Failed to route to DLQ: " + dlqEx.getMessage()));
            log.error("Failed to route outbox event {} to DLQ", event.getId(), dlqEx);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH ? message : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
