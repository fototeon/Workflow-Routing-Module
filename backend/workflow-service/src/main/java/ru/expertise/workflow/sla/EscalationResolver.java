package ru.expertise.workflow.sla;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Determines elapsed-time percentage against an SLA window and which escalation step (if any) newly applies. */
@Component
public class EscalationResolver {

    private final ObjectMapper objectMapper;

    public EscalationResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<EscalationRule> parseRules(JsonNode escalationRulesJson) {
        if (escalationRulesJson == null || !escalationRulesJson.isArray()) {
            return List.of();
        }
        List<EscalationRule> rules = new ArrayList<>();
        for (JsonNode node : escalationRulesJson) {
            rules.add(objectMapper.convertValue(node, EscalationRule.class));
        }
        return rules;
    }

    public int elapsedPercent(Instant windowStart, Instant windowEnd, Instant now) {
        long totalSeconds = Duration.between(windowStart, windowEnd).getSeconds();
        if (totalSeconds <= 0) {
            return 100;
        }
        long elapsedSeconds = Duration.between(windowStart, now).getSeconds();
        int percent = (int) Math.round((elapsedSeconds * 100.0) / totalSeconds);
        return Math.max(0, Math.min(percent, 100));
    }

    /** The highest-threshold rule that is due (afterPercent &lt;= elapsedPercent) but not yet applied (afterPercent &gt; lastEscalatedPercent). */
    public Optional<EscalationRule> nextDueRule(List<EscalationRule> rules, int elapsedPercent, int lastEscalatedPercent) {
        return rules.stream()
                .filter(rule -> rule.afterPercent() <= elapsedPercent && rule.afterPercent() > lastEscalatedPercent)
                .max(Comparator.comparingInt(EscalationRule::afterPercent));
    }
}
