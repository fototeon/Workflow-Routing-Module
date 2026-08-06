package ru.expertise.workflow.routing;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** Pure, side-effect-free evaluator of a routing condition against an arbitrary attribute context. */
@Component
public class RoutingConditionEvaluator {

    public boolean evaluate(LeafCondition condition, Map<String, Object> context) {
        Object actual = context.get(condition.field());
        if (actual == null) {
            // A comparison against a missing attribute is simply not satisfied.
            return false;
        }
        return switch (condition.op()) {
            case EQ -> valuesEqual(actual, condition.value());
            case NEQ -> !valuesEqual(actual, condition.value());
            case GT -> compareOrdered(actual, condition.value()) > 0;
            case GTE -> compareOrdered(actual, condition.value()) >= 0;
            case LT -> compareOrdered(actual, condition.value()) < 0;
            case LTE -> compareOrdered(actual, condition.value()) <= 0;
        };
    }

    /** Numbers compare numerically ("1000" equals 1000); everything else compares as text. */
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
