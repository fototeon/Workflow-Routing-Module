package ru.expertise.workflow.sla;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.expertise.workflow.domain.SlaPolicy;
import ru.expertise.workflow.audit.AuditContext;
import ru.expertise.workflow.audit.Audited;
import ru.expertise.workflow.config.WorkflowProperties;
import ru.expertise.workflow.events.DomainEventType;
import ru.expertise.workflow.events.OutboxEventWriter;
import ru.expertise.workflow.repository.SlaPolicyRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SlaPolicyService {

    private static final String SUBJECT = "SlaPolicy";

    private final SlaPolicyRepository slaPolicyRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final ru.expertise.workflow.repository.ProcessEventLogRepository processEventLogRepository;
    private final WorkflowProperties properties;

    public SlaPolicyService(SlaPolicyRepository slaPolicyRepository,
                             OutboxEventWriter outboxEventWriter,
                             ru.expertise.workflow.repository.ProcessEventLogRepository processEventLogRepository,
                             WorkflowProperties properties) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.processEventLogRepository = processEventLogRepository;
        this.properties = properties;
    }

    /** Journals the change and publishes it, so SLA edits are auditable like process actions (TZ §10). */
    private void recordChange(DomainEventType type, SlaPolicy policy) {
        Map<String, Object> payload = Map.of(
                "code", policy.getCode(),
                "name", policy.getName(),
                "durationMinutes", policy.getDurationMinutes(),
                "businessHoursOnly", policy.isBusinessHoursOnly());
        AuditContext.subject(SUBJECT, policy.getId());
        payload.forEach(AuditContext::detail);
        outboxEventWriter.enqueue(SUBJECT, policy.getId().toString(), type,
                properties.getKafka().getTopicDefinitionEvents(), null, null, payload);
    }

    @Transactional
    @Audited(DomainEventType.SLA_POLICY_CREATED)
    public SlaPolicy create(String code, String name, int durationMinutes, boolean businessHoursOnly, JsonNode escalationRules) {
        SlaPolicy policy = new SlaPolicy();
        policy.setCode(code);
        policy.setName(name);
        policy.setDurationMinutes(durationMinutes);
        policy.setBusinessHoursOnly(businessHoursOnly);
        policy.setEscalationRules(escalationRules);
        policy = slaPolicyRepository.save(policy);

        recordChange(DomainEventType.SLA_POLICY_CREATED, policy);
        return policy;
    }

    @Transactional
    @Audited(DomainEventType.SLA_POLICY_UPDATED)
    public SlaPolicy update(UUID id, String name, int durationMinutes, boolean businessHoursOnly, JsonNode escalationRules) {
        SlaPolicy policy = get(id);
        policy.setName(name);
        policy.setDurationMinutes(durationMinutes);
        policy.setBusinessHoursOnly(businessHoursOnly);
        policy.setEscalationRules(escalationRules);
        policy = slaPolicyRepository.save(policy);

        recordChange(DomainEventType.SLA_POLICY_UPDATED, policy);
        return policy;
    }

    @Transactional(readOnly = true)
    public SlaPolicy get(UUID id) {
        return slaPolicyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SlaPolicy " + id + " not found"));
    }

    /** Configuration journal of an SLA policy (TZ §10). */
    @Transactional(readOnly = true)
    public List<ru.expertise.workflow.domain.ProcessEventLog> getJournal(UUID id) {
        return processEventLogRepository.findBySubjectTypeAndSubjectIdOrderByOccurredAtAsc(SUBJECT, id.toString());
    }

    @Transactional(readOnly = true)
    public List<SlaPolicy> list() {
        return slaPolicyRepository.findAll();
    }
}
