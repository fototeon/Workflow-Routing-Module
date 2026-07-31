package ru.expertise.workflow.process;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.expertise.workflow.domain.ProcessDefinition;
import ru.expertise.workflow.domain.ProcessDefinitionStatus;
import ru.expertise.workflow.domain.RoutingRule;
import ru.expertise.workflow.domain.SlaPolicy;
import ru.expertise.workflow.repository.ProcessDefinitionRepository;
import ru.expertise.workflow.repository.RoutingRuleRepository;
import ru.expertise.workflow.repository.SlaPolicyRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ProcessDefinitionService {

    private final ProcessDefinitionRepository processDefinitionRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final SlaPolicyRepository slaPolicyRepository;

    public ProcessDefinitionService(ProcessDefinitionRepository processDefinitionRepository,
                                     RoutingRuleRepository routingRuleRepository,
                                     SlaPolicyRepository slaPolicyRepository) {
        this.processDefinitionRepository = processDefinitionRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.slaPolicyRepository = slaPolicyRepository;
    }

    @Transactional
    public ProcessDefinition create(String code, String name, UUID slaPolicyId) {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setCode(code);
        definition.setName(name);
        definition.setVersion(nextVersion(code));
        definition.setStatus(ProcessDefinitionStatus.DRAFT);
        definition.setSlaPolicy(resolveSlaPolicy(slaPolicyId));
        return processDefinitionRepository.save(definition);
    }

    @Transactional
    public ProcessDefinition update(UUID id, String name, UUID slaPolicyId) {
        ProcessDefinition definition = get(id);
        if (definition.getStatus() != ProcessDefinitionStatus.DRAFT) {
            throw new InvalidStatusTransitionException(definition.getStatus().name(), "EDITED");
        }
        definition.setName(name);
        definition.setSlaPolicy(resolveSlaPolicy(slaPolicyId));
        return processDefinitionRepository.save(definition);
    }

    @Transactional
    public ProcessDefinition publish(UUID id) {
        ProcessDefinition definition = get(id);
        if (definition.getStatus() != ProcessDefinitionStatus.DRAFT) {
            throw new InvalidStatusTransitionException(definition.getStatus().name(), ProcessDefinitionStatus.PUBLISHED.name());
        }
        if (routingRuleRepository.findByProcessDefinitionIdOrderByPriorityAsc(id).isEmpty()) {
            throw new IllegalStateException("Cannot publish a process definition with no routing rules");
        }
        definition.setStatus(ProcessDefinitionStatus.PUBLISHED);
        return processDefinitionRepository.save(definition);
    }

    @Transactional
    public ProcessDefinition archive(UUID id) {
        ProcessDefinition definition = get(id);
        if (definition.getStatus() == ProcessDefinitionStatus.ARCHIVED) {
            return definition;
        }
        definition.setStatus(ProcessDefinitionStatus.ARCHIVED);
        return processDefinitionRepository.save(definition);
    }

    @Transactional
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
        return routingRuleRepository.save(rule);
    }

    @Transactional
    public void deleteRoutingRule(UUID definitionId, UUID ruleId) {
        ProcessDefinition definition = get(definitionId);
        if (definition.getStatus() != ProcessDefinitionStatus.DRAFT) {
            throw new InvalidStatusTransitionException(definition.getStatus().name(), "ROUTING_RULE_DELETED");
        }
        RoutingRule rule = routingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("RoutingRule " + ruleId + " not found"));
        routingRuleRepository.delete(rule);
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

    @Transactional(readOnly = true)
    public List<ProcessDefinition> listVersions(String code) {
        return processDefinitionRepository.findByCodeOrderByVersionDesc(code);
    }

    @Transactional(readOnly = true)
    public List<ProcessDefinition> listPublished() {
        return processDefinitionRepository.findByStatusOrderByCodeAscVersionDesc(ProcessDefinitionStatus.PUBLISHED);
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
