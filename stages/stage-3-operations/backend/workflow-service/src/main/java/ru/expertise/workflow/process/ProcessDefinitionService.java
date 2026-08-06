package ru.expertise.workflow.process;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.expertise.workflow.domain.ProcessDefinition;
import ru.expertise.workflow.domain.ProcessDefinitionStatus;
import ru.expertise.workflow.domain.ProcessEventLog;
import ru.expertise.workflow.domain.RoutingRule;
import ru.expertise.workflow.domain.SlaPolicy;
import ru.expertise.workflow.repository.ProcessDefinitionRepository;
import ru.expertise.workflow.repository.ProcessEventLogRepository;
import ru.expertise.workflow.repository.RoutingRuleRepository;
import ru.expertise.workflow.audit.AuditContext;
import ru.expertise.workflow.audit.Audited;
import ru.expertise.workflow.config.WorkflowProperties;
import ru.expertise.workflow.events.DomainEventType;
import ru.expertise.workflow.events.OutboxEventWriter;
import ru.expertise.workflow.repository.SlaPolicyRepository;

import java.util.Map;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProcessDefinitionService {

    private static final String SUBJECT_DEFINITION = "ProcessDefinition";

    private final ProcessDefinitionRepository processDefinitionRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final ProcessEventLogRepository processEventLogRepository;
    private final WorkflowProperties properties;

    public ProcessDefinitionService(ProcessDefinitionRepository processDefinitionRepository,
                                     RoutingRuleRepository routingRuleRepository,
                                     SlaPolicyRepository slaPolicyRepository,
                                     OutboxEventWriter outboxEventWriter,
                                     ProcessEventLogRepository processEventLogRepository,
                                     WorkflowProperties properties) {
        this.processDefinitionRepository = processDefinitionRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.processEventLogRepository = processEventLogRepository;
        this.properties = properties;
    }

    /** Journals a configuration change and publishes it, so template edits are auditable (TZ §10, REQ-02-002). */
    private void recordConfigChange(DomainEventType type, String subjectType, UUID subjectId, Map<String, Object> payload) {
        AuditContext.subject(subjectType, subjectId);
        payload.forEach(AuditContext::detail);
        outboxEventWriter.enqueue(subjectType, subjectId.toString(), type,
                properties.getKafka().getTopicDefinitionEvents(), null, null, payload);
    }

    @Transactional
    @Audited(DomainEventType.DEFINITION_CREATED)
    public ProcessDefinition create(String code, String name, UUID slaPolicyId) {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setCode(code);
        definition.setName(name);
        definition.setVersion(nextVersion(code));
        definition.setStatus(ProcessDefinitionStatus.DRAFT);
        definition.setSlaPolicy(resolveSlaPolicy(slaPolicyId));
        definition = processDefinitionRepository.save(definition);

        recordConfigChange(DomainEventType.DEFINITION_CREATED, SUBJECT_DEFINITION, definition.getId(),
                Map.of("code", definition.getCode(), "name", definition.getName(), "version", definition.getVersion()));
        return definition;
    }

    @Transactional
    @Audited(DomainEventType.DEFINITION_UPDATED)
    public ProcessDefinition update(UUID id, String name, UUID slaPolicyId) {
        ProcessDefinition definition = get(id);
        if (definition.getStatus() != ProcessDefinitionStatus.DRAFT) {
            throw new InvalidStatusTransitionException(definition.getStatus().name(), "EDITED");
        }
        definition.setName(name);
        definition.setSlaPolicy(resolveSlaPolicy(slaPolicyId));
        definition = processDefinitionRepository.save(definition);

        recordConfigChange(DomainEventType.DEFINITION_UPDATED, SUBJECT_DEFINITION, definition.getId(),
                Map.of("code", definition.getCode(), "name", definition.getName(), "version", definition.getVersion()));
        return definition;
    }

    @Transactional
    @Audited(DomainEventType.DEFINITION_PUBLISHED)
    public ProcessDefinition publish(UUID id) {
        ProcessDefinition definition = get(id);
        if (definition.getStatus() != ProcessDefinitionStatus.DRAFT) {
            throw new InvalidStatusTransitionException(definition.getStatus().name(), ProcessDefinitionStatus.PUBLISHED.name());
        }
        if (routingRuleRepository.findByProcessDefinitionIdOrderByPriorityAsc(id).isEmpty()) {
            throw new IllegalStateException("Cannot publish a process definition with no routing rules");
        }
        definition.setStatus(ProcessDefinitionStatus.PUBLISHED);
        definition = processDefinitionRepository.save(definition);

        recordConfigChange(DomainEventType.DEFINITION_PUBLISHED, SUBJECT_DEFINITION, definition.getId(),
                Map.of("code", definition.getCode(), "version", definition.getVersion()));
        return definition;
    }

    @Transactional
    @Audited(DomainEventType.DEFINITION_ARCHIVED)
    public ProcessDefinition archive(UUID id) {
        ProcessDefinition definition = get(id);
        definition.setStatus(ProcessDefinitionStatus.ARCHIVED);
        definition = processDefinitionRepository.save(definition);

        recordConfigChange(DomainEventType.DEFINITION_ARCHIVED, SUBJECT_DEFINITION, definition.getId(),
                Map.of("code", definition.getCode(), "version", definition.getVersion()));
        return definition;
    }

    @Transactional
    @Audited(DomainEventType.ROUTING_RULE_ADDED)
    public RoutingRule addRoutingRule(UUID definitionId, String name, int priority,
                                       JsonNode conditionTree,
                                       String targetStepCode, String targetRole) {
        ProcessDefinition definition = get(definitionId);
        if (definition.getStatus() != ProcessDefinitionStatus.DRAFT) {
            throw new InvalidStatusTransitionException(definition.getStatus().name(), "ROUTING_RULE_ADDED");
        }
        RoutingRule rule = new RoutingRule();
        rule.setProcessDefinition(definition);
        rule.setName(name);
        rule.setPriority(priority);
        rule.setConditionTree(conditionTree);
        rule.setTargetStepCode(targetStepCode);
        rule.setTargetRole(targetRole);
        rule = routingRuleRepository.save(rule);

        recordConfigChange(DomainEventType.ROUTING_RULE_ADDED, SUBJECT_DEFINITION, definitionId,
                Map.of("routingRuleId", rule.getId(), "name", rule.getName(),
                        "priority", rule.getPriority(), "targetStepCode", rule.getTargetStepCode()));
        return rule;
    }

    @Transactional
    @Audited(DomainEventType.ROUTING_RULE_REMOVED)
    public void deleteRoutingRule(UUID definitionId, UUID ruleId) {
        ProcessDefinition definition = get(definitionId);
        if (definition.getStatus() != ProcessDefinitionStatus.DRAFT) {
            throw new InvalidStatusTransitionException(definition.getStatus().name(), "ROUTING_RULE_DELETED");
        }
        RoutingRule rule = routingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("RoutingRule " + ruleId + " not found"));
        routingRuleRepository.delete(rule);

        recordConfigChange(DomainEventType.ROUTING_RULE_REMOVED, SUBJECT_DEFINITION, definitionId,
                Map.of("routingRuleId", ruleId, "name", rule.getName()));
    }

    /** Configuration journal of a template: creation, edits, rule changes and publication (TZ §10). */
    @Transactional(readOnly = true)
    public List<ProcessEventLog> getJournal(UUID definitionId) {
        return processEventLogRepository.findBySubjectTypeAndSubjectIdOrderByOccurredAtAsc(
                SUBJECT_DEFINITION, definitionId.toString());
    }

    @Transactional(readOnly = true)
    public List<RoutingRule> getRoutingRules(UUID definitionId) {
        return routingRuleRepository.findByProcessDefinitionIdOrderByPriorityAsc(definitionId);
    }

    @Transactional(readOnly = true)
    public ProcessDefinition get(UUID id) {
        return processDefinitionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ProcessDefinition " + id + " not found"));
    }

    /**
     * A template together with how many routing rules it carries. The catalogue screen needs the
     * count to show why a draft cannot be published yet ({@link #publish} rejects rule-less drafts).
     */
    public record DefinitionSummary(ProcessDefinition definition, long routingRuleCount) {
    }

    /**
     * The template catalogue: every version of one {@code code}, or every template in one
     * {@code status}, or — when both are null — the whole registry including drafts and archives.
     */
    @Transactional(readOnly = true)
    public List<DefinitionSummary> list(String code, ProcessDefinitionStatus status) {
        List<ProcessDefinition> definitions;
        if (code != null && !code.isBlank()) {
            definitions = processDefinitionRepository.findByCodeOrderByVersionDesc(code);
        } else if (status == null) {
            definitions = processDefinitionRepository.findAllByOrderByCodeAscVersionDesc();
        } else {
            definitions = processDefinitionRepository.findByStatusOrderByCodeAscVersionDesc(status);
        }
        return withRuleCounts(definitions);
    }

    @Transactional(readOnly = true)
    public DefinitionSummary getSummary(UUID id) {
        return withRuleCounts(List.of(get(id))).getFirst();
    }

    private List<DefinitionSummary> withRuleCounts(List<ProcessDefinition> definitions) {
        if (definitions.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> counts = routingRuleRepository
                .countByProcessDefinitionIds(definitions.stream().map(ProcessDefinition::getId).toList()).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
        return definitions.stream()
                .map(definition -> new DefinitionSummary(definition, counts.getOrDefault(definition.getId(), 0L)))
                .toList();
    }

    private int nextVersion(String code) {
        return processDefinitionRepository.findByCodeOrderByVersionDesc(code).stream()
                .findFirst()
                .map(d -> d.getVersion() + 1)
                .orElse(1);
    }

    private SlaPolicy resolveSlaPolicy(UUID slaPolicyId) {
        if (slaPolicyId == null) {
            return null;
        }
        return slaPolicyRepository.findById(slaPolicyId)
                .orElseThrow(() -> new EntityNotFoundException("SlaPolicy " + slaPolicyId + " not found"));
    }
}
