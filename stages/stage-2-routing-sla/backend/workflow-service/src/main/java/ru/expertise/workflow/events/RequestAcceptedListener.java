package ru.expertise.workflow.events;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.expertise.workflow.domain.ProcessDefinitionStatus;
import ru.expertise.workflow.process.ProcessInstanceService;
import ru.expertise.workflow.repository.ProcessDefinitionRepository;

import java.util.Map;

/**
 * Consumes the inbound {@code RequestAccepted} event (REQ-02-001) and starts a process instance.
 * The publishing side (request-service) is out of scope for this module; the expected envelope
 * shape is {@code {eventId, eventType, version, occurredAt, correlationId, businessKey,
 * payload:{processDefinitionCode, attributes}}}.
 */
@Component
public class RequestAcceptedListener {

    private static final Logger log = LoggerFactory.getLogger(RequestAcceptedListener.class);

    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ProcessInstanceService processInstanceService;
    private final ObjectMapper objectMapper;

    public RequestAcceptedListener(ProcessDefinitionRepository processDefinitionRepository,
                                    ProcessInstanceService processInstanceService,
                                    ObjectMapper objectMapper) {
        this.processDefinitionRepository = processDefinitionRepository;
        this.processInstanceService = processInstanceService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${workflow.kafka.topic-request-accepted}")
    public void onRequestAccepted(JsonNode envelope) {
        JsonNode payload = envelope.path("payload");
        String processDefinitionCode = payload.path("processDefinitionCode").asText(null);
        String businessKey = envelope.path("businessKey").asText(null);
        String correlationId = envelope.path("correlationId").asText(null);

        if (processDefinitionCode == null || businessKey == null) {
            log.warn("Ignoring malformed RequestAccepted event (missing processDefinitionCode/businessKey): {}", envelope);
            return;
        }

        var definition = processDefinitionRepository
                .findFirstByCodeAndStatusOrderByVersionDesc(processDefinitionCode, ProcessDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No PUBLISHED process definition with code " + processDefinitionCode));

        Map<String, Object> attributes = payload.has("attributes")
                ? objectMapper.convertValue(payload.get("attributes"), new TypeReference<Map<String, Object>>() { })
                : Map.of();

        processInstanceService.startInstance(definition.getId(), businessKey, attributes, correlationId);
    }
}
