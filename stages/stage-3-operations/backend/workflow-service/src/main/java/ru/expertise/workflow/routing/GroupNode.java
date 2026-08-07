package ru.expertise.workflow.routing;

import java.util.List;

/** {@code NOT} requires exactly one child; {@code AND}/{@code OR} accept one or more. */
public record GroupNode(LogicalOperator op, List<ConditionNode> children) implements ConditionNode {
}
