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

    private ConditionNode parse(String json) throws Exception {
        JsonNode tree = objectMapper.readTree(json);
        return parser.parse(tree);
    }

    @Test
    void evaluatesSimpleEqualityLeaf() throws Exception {
        ConditionNode node = parse("""
                {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"}
                """);

        assertThat(evaluator.evaluate(node, Map.of("requestType", "COMPLEX"))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("requestType", "SIMPLE"))).isFalse();
    }

    @Test
    void evaluatesNumericComparison() throws Exception {
        ConditionNode node = parse("""
                {"type":"condition","field":"riskScore","op":"GTE","value":75}
                """);

        assertThat(evaluator.evaluate(node, Map.of("riskScore", 80))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("riskScore", 75))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("riskScore", 10))).isFalse();
    }

    @Test
    void evaluatesAndGroup() throws Exception {
        ConditionNode node = parse("""
                {"type":"group","op":"AND","children":[
                  {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"},
                  {"type":"condition","field":"riskScore","op":"GT","value":50}
                ]}
                """);

        assertThat(evaluator.evaluate(node, Map.of("requestType", "COMPLEX", "riskScore", 60))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("requestType", "COMPLEX", "riskScore", 10))).isFalse();
        assertThat(evaluator.evaluate(node, Map.of("requestType", "SIMPLE", "riskScore", 60))).isFalse();
    }

    @Test
    void evaluatesOrGroup() throws Exception {
        ConditionNode node = parse("""
                {"type":"group","op":"OR","children":[
                  {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"},
                  {"type":"condition","field":"requestType","op":"EQ","value":"URGENT"}
                ]}
                """);

        assertThat(evaluator.evaluate(node, Map.of("requestType", "URGENT"))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("requestType", "SIMPLE"))).isFalse();
    }

    @Test
    void evaluatesNestedGroupsAndNot() throws Exception {
        ConditionNode node = parse("""
                {"type":"group","op":"AND","children":[
                  {"type":"group","op":"NOT","children":[
                    {"type":"condition","field":"vip","op":"EQ","value":true}
                  ]},
                  {"type":"condition","field":"competency","op":"IN","value":["LEGAL","TAX"]}
                ]}
                """);

        assertThat(evaluator.evaluate(node, Map.of("vip", false, "competency", "TAX"))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("vip", true, "competency", "TAX"))).isFalse();
        assertThat(evaluator.evaluate(node, Map.of("vip", false, "competency", "MEDICAL"))).isFalse();
    }

    @Test
    void missingAttributeIsNotSatisfiedExceptForExistsCheck() throws Exception {
        ConditionNode eq = parse("""
                {"type":"condition","field":"missingField","op":"EQ","value":"x"}
                """);
        assertThat(evaluator.evaluate(eq, Map.of())).isFalse();

        ConditionNode existsFalse = parse("""
                {"type":"condition","field":"missingField","op":"EXISTS","value":false}
                """);
        assertThat(evaluator.evaluate(existsFalse, Map.of())).isTrue();

        ConditionNode existsTrue = parse("""
                {"type":"condition","field":"presentField","op":"EXISTS","value":true}
                """);
        assertThat(evaluator.evaluate(existsTrue, Map.of("presentField", "y"))).isTrue();
    }

    @Test
    void containsOperatorMatchesSubstring() throws Exception {
        ConditionNode node = parse("""
                {"type":"condition","field":"description","op":"CONTAINS","value":"экспертиза"}
                """);

        assertThat(evaluator.evaluate(node, Map.of("description", "дополнительная экспертиза требуется"))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("description", "обычная проверка"))).isFalse();
    }

    @Test
    void rejectsEmptyGroupChildren() throws Exception {
        ConditionNode node = parse("""
                {"type":"group","op":"AND","children":[]}
                """);

        assertThatThrownBy(() -> evaluator.evaluate(node, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
