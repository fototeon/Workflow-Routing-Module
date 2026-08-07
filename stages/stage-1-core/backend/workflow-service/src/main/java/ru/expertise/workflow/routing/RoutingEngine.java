package ru.expertise.workflow.routing;

import org.springframework.stereotype.Service;
import ru.expertise.workflow.domain.RoutingRule;
import ru.expertise.workflow.repository.RoutingRuleRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the next process step by evaluating a process definition's routing rules (ordered by
 * priority ascending) against the supplied attribute context. First match wins (REQ-02-004).
 */
@Service
public class RoutingEngine {

    private final RoutingRuleRepository routingRuleRepository;
    private final RoutingConditionParser parser;
    private final RoutingConditionEvaluator evaluator;

    public RoutingEngine(RoutingRuleRepository routingRuleRepository,
                          RoutingConditionParser parser,
                          RoutingConditionEvaluator evaluator) {
        this.routingRuleRepository = routingRuleRepository;
        this.parser = parser;
        this.evaluator = evaluator;
    }

    public RoutingDecision resolve(UUID processDefinitionId, Map<String, Object> context) {
        List<RoutingRule> rules = routingRuleRepository.findByProcessDefinitionIdOrderByPriorityAsc(processDefinitionId);
        for (RoutingRule rule : rules) {
            if (evaluator.evaluate(parser.parse(rule.getConditionTree()), context)) {
                return new RoutingDecision(rule.getId(), rule.getTargetStepCode(), rule.getTargetRole());
            }
        }
        throw new NoRouteMatchedException(processDefinitionId);
    }
}
