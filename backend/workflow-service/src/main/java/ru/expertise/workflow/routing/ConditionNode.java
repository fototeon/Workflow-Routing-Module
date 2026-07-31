package ru.expertise.workflow.routing;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A node in the JSON condition tree stored on {@code RoutingRule.conditionTree}. Two shapes:
 * <pre>
 * {"type":"group","op":"AND","children":[...]}
 * {"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"}
 * </pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GroupNode.class, name = "group"),
        @JsonSubTypes.Type(value = LeafCondition.class, name = "condition")
})
public sealed interface ConditionNode permits GroupNode, LeafCondition {
}
