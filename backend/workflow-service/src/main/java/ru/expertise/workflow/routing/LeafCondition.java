package ru.expertise.workflow.routing;

import com.fasterxml.jackson.databind.JsonNode;

/** A single leaf comparison: {@code context.get(field) <op> value}. {@code value} may be a scalar or array (for IN/NOT_IN). */
public record LeafCondition(String field, ComparisonOperator op, JsonNode value) implements ConditionNode {
}
