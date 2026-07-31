package ru.expertise.workflow.routing;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/** Pure, side-effect-free evaluator for the routing condition tree against an arbitrary attribute context. */
@Component
public class RoutingConditionEvaluator {

    public boolean evaluate(ConditionNode node, Map<String, Object> context) {
        return switch (node) {
            case GroupNode group -> evaluateGroup(group, context);
            case LeafCondition leaf -> evaluateLeaf(leaf, context);
        };
    }

    private boolean evaluateGroup(GroupNode group, Map<String, Object> context) {
        if (group.children() == null || group.children().isEmpty()) {
            throw new IllegalArgumentException("Group node '" + group.op() + "' must have at least one child");
        }
        return switch (group.op()) {
            case AND -> group.children().stream().allMatch(child -> evaluate(child, context));
            case OR -> group.children().stream().anyMatch(child -> evaluate(child, context));
            case NOT -> {
                if (group.children().size() != 1) {
                    throw new IllegalArgumentException("NOT group must have exactly one child");
                }
                yield !evaluate(group.children().get(0), context);
            }
        };
    }

    private boolean evaluateLeaf(LeafCondition leaf, Map<String, Object> context) {
        Object actual = context.get(leaf.field());

        if (leaf.op() == ComparisonOperator.EXISTS) {
            boolean expectPresent = leaf.value() == null || leaf.value().isNull() || leaf.value().asBoolean(true);
            return expectPresent == (actual != null);
        }

        if (actual == null) {
            // Any comparison other than EXISTS against a missing attribute is simply not satisfied.
            return false;
        }

        return switch (leaf.op()) {
            case EQ -> valuesEqual(actual, leaf.value());
            case NEQ -> !valuesEqual(actual, leaf.value());
            case GT -> compareOrdered(actual, leaf.value()) > 0;
            case GTE -> compareOrdered(actual, leaf.value()) >= 0;
            case LT -> compareOrdered(actual, leaf.value()) < 0;
            case LTE -> compareOrdered(actual, leaf.value()) <= 0;
            case IN -> containsValue(leaf.value(), actual);
            case NOT_IN -> !containsValue(leaf.value(), actual);
            case CONTAINS -> containsSubstring(actual, leaf.value());
            case EXISTS -> throw new IllegalStateException("handled above");
        };
    }

    private boolean valuesEqual(Object actual, JsonNode expected) {
        BigDecimal actualNum = toNumeric(actual);
        BigDecimal expectedNum = toNumeric(expected);
        if (actualNum != null && expectedNum != null) {
            return actualNum.compareTo(expectedNum) == 0;
        }
        return Objects.equals(String.valueOf(actual), expected.asText());
    }

    private int compareOrdered(Object actual, JsonNode expected) {
        BigDecimal actualNum = toNumeric(actual);
        BigDecimal expectedNum = toNumeric(expected);
        if (actualNum == null || expectedNum == null) {
            throw new IllegalArgumentException("GT/GTE/LT/LTE require numeric operands, got: " + actual + " vs " + expected);
        }
        return actualNum.compareTo(expectedNum);
    }

    private boolean containsValue(JsonNode expectedArray, Object actual) {
        if (expectedArray == null || !expectedArray.isArray()) {
            throw new IllegalArgumentException("IN/NOT_IN require an array value");
        }
        Iterator<JsonNode> it = expectedArray.elements();
        while (it.hasNext()) {
            if (valuesEqual(actual, it.next())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSubstring(Object actual, JsonNode expected) {
        return String.valueOf(actual).contains(expected.asText());
    }

    private BigDecimal toNumeric(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String str) {
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal toNumeric(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
