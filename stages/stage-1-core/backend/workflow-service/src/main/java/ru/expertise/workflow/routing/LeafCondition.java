package ru.expertise.workflow.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * One comparison against the process attributes: {@code attributes.get(field) <op> value}. It is
 * stored as JSON on the routing rule:
 * <pre>{"type":"condition","field":"requestType","op":"EQ","value":"COMPLEX"}</pre>
 * The {@code type} discriminator is accepted and ignored — a rule carries exactly one condition at
 * this stage, and the discriminator only starts to matter once conditions can be combined into
 * AND/OR trees.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LeafCondition(String field, ComparisonOperator op, JsonNode value) {
}
