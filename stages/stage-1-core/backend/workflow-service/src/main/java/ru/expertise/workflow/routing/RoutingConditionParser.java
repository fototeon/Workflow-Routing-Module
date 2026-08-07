package ru.expertise.workflow.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Converts the raw JSONB condition stored on a routing rule into a typed {@link LeafCondition}. */
@Component
public class RoutingConditionParser {

    private final ObjectMapper objectMapper;

    public RoutingConditionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LeafCondition parse(JsonNode tree) {
        if (tree == null || tree.isNull() || tree.isMissingNode()) {
            throw new IllegalArgumentException("Condition must not be empty");
        }
        return objectMapper.convertValue(tree, LeafCondition.class);
    }
}
