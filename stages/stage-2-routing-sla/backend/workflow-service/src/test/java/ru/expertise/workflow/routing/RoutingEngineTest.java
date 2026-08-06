package ru.expertise.workflow.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.expertise.workflow.domain.RoutingRule;
import ru.expertise.workflow.repository.RoutingRuleRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RoutingRuleRepository routingRuleRepository;

    private RoutingEngine engineWithMockRepo() {
        return new RoutingEngine(routingRuleRepository, new RoutingConditionParser(objectMapper), new RoutingConditionEvaluator());
    }

    private RoutingRule rule(int priority, String conditionJson, String targetStep, String targetRole) {
        RoutingRule r = new RoutingRule();
        try {
            r.setPriority(priority);
            r.setConditionTree(objectMapper.readTree(conditionJson));
            r.setTargetStepCode(targetStep);
            r.setTargetRole(targetRole);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r;
    }

    @Test
    void firstMatchingRuleByPriorityWins() {
        UUID definitionId = UUID.randomUUID();
        RoutingRule high = rule(0, """
                {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"}
                """, "EXPERT_REVIEW", "MANAGER");
        RoutingRule fallback = rule(10, """
                {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"}
                """, "SIMPLE_REVIEW", "COORDINATOR");

        when(routingRuleRepository.findByProcessDefinitionIdOrderByPriorityAsc(definitionId))
                .thenReturn(List.of(high, fallback));

        RoutingDecision decision = engineWithMockRepo().resolve(definitionId, Map.of("requestType", "COMPLEX"));

        assertThat(decision.targetStepCode()).isEqualTo("EXPERT_REVIEW");
        assertThat(decision.targetRole()).isEqualTo("MANAGER");
    }

    @Test
    void throwsWhenNoRuleMatches() {
        UUID definitionId = UUID.randomUUID();
        RoutingRule rule = rule(0, """
                {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"}
                """, "EXPERT_REVIEW", "MANAGER");

        when(routingRuleRepository.findByProcessDefinitionIdOrderByPriorityAsc(definitionId))
                .thenReturn(List.of(rule));

        assertThatThrownBy(() -> engineWithMockRepo().resolve(definitionId, Map.of("requestType", "SIMPLE")))
                .isInstanceOf(NoRouteMatchedException.class);
    }
}
