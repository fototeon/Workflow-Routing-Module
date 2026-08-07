package ru.expertise.workflow.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingConditionEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoutingConditionParser parser = new RoutingConditionParser(objectMapper);
    private final RoutingConditionEvaluator evaluator = new RoutingConditionEvaluator();

    private LeafCondition parse(String json) throws Exception {
        JsonNode tree = objectMapper.readTree(json);
        return parser.parse(tree);
    }

    @Test
    void evaluatesSimpleEquality() throws Exception {
        LeafCondition condition = parse("""
                {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"}
                """);

        assertThat(evaluator.evaluate(condition, Map.of("requestType", "COMPLEX"))).isTrue();
        assertThat(evaluator.evaluate(condition, Map.of("requestType", "SIMPLE"))).isFalse();
    }

    @Test
    void evaluatesNumericComparison() throws Exception {
        LeafCondition condition = parse("""
                {"type":"condition","field":"riskScore","op":"GTE","value":75}
                """);

        assertThat(evaluator.evaluate(condition, Map.of("riskScore", 80))).isTrue();
        assertThat(evaluator.evaluate(condition, Map.of("riskScore", 75))).isTrue();
        assertThat(evaluator.evaluate(condition, Map.of("riskScore", 10))).isFalse();
    }

    @Test
    void comparesNumbersWrittenAsTextNumerically() throws Exception {
        LeafCondition condition = parse("""
                {"type":"condition","field":"amount","op":"EQ","value":1000}
                """);

        assertThat(evaluator.evaluate(condition, Map.of("amount", "1000"))).isTrue();
    }

    @Test
    void missingAttributeIsNeverSatisfied() throws Exception {
        LeafCondition equals = parse("""
                {"type":"condition","field":"missingField","op":"EQ","value":"x"}
                """);
        LeafCondition notEquals = parse("""
                {"type":"condition","field":"missingField","op":"NEQ","value":"x"}
                """);

        assertThat(evaluator.evaluate(equals, Map.of())).isFalse();
        assertThat(evaluator.evaluate(notEquals, Map.of())).isFalse();
    }

    @Test
    void orderingOperatorsRequireNumbers() throws Exception {
        LeafCondition condition = parse("""
                {"type":"condition","field":"requestType","op":"GT","value":"COMPLEX"}
                """);

        assertThatThrownBy(() -> evaluator.evaluate(condition, Map.of("requestType", "SIMPLE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyCondition() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
