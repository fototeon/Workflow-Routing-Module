package ru.expertise.workflow.sla;

/** One step of {@code SlaPolicy.escalationRules}: e.g. {"afterPercent":80,"escalateToRole":"MANAGER"}. */
public record EscalationRule(int afterPercent, String escalateToRole) {
}
