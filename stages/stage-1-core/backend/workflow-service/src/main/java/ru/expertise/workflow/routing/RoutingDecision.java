package ru.expertise.workflow.routing;

import java.util.UUID;

public record RoutingDecision(UUID matchedRuleId, String targetStepCode, String targetRole) {
}
