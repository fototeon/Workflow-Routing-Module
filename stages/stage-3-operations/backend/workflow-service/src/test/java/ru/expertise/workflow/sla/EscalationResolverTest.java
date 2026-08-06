package ru.expertise.workflow.sla;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EscalationResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EscalationResolver resolver = new EscalationResolver(objectMapper);

    private JsonNode rulesJson() throws Exception {
        return objectMapper.readTree("""
                [{"afterPercent":50,"escalateToRole":"COORDINATOR"},
                 {"afterPercent":80,"escalateToRole":"MANAGER"}]
                """);
    }

    @Test
    void parsesEscalationRulesInOrder() throws Exception {
        List<EscalationRule> rules = resolver.parseRules(rulesJson());
        assertThat(rules).extracting(EscalationRule::afterPercent).containsExactly(50, 80);
        assertThat(rules).extracting(EscalationRule::escalateToRole).containsExactly("COORDINATOR", "MANAGER");
    }

    @Test
    void elapsedPercentIsComputedAgainstWindow() {
        Instant start = Instant.parse("2026-07-27T09:00:00Z");
        Instant end = Instant.parse("2026-07-27T19:00:00Z"); // 10h window
        Instant now = start.plus(5, ChronoUnit.HOURS);

        assertThat(resolver.elapsedPercent(start, end, now)).isEqualTo(50);
    }

    @Test
    void elapsedPercentClampsToHundred() {
        Instant start = Instant.parse("2026-07-27T09:00:00Z");
        Instant end = Instant.parse("2026-07-27T19:00:00Z");
        Instant now = end.plus(1, ChronoUnit.HOURS);

        assertThat(resolver.elapsedPercent(start, end, now)).isEqualTo(100);
    }

    @Test
    void nextDueRulePicksHighestNewlyCrossedThreshold() throws Exception {
        List<EscalationRule> rules = resolver.parseRules(rulesJson());

        Optional<EscalationRule> due = resolver.nextDueRule(rules, 85, 0);
        assertThat(due).isPresent();
        assertThat(due.get().afterPercent()).isEqualTo(80);
    }

    @Test
    void nextDueRuleSkipsAlreadyAppliedThresholds() throws Exception {
        List<EscalationRule> rules = resolver.parseRules(rulesJson());

        Optional<EscalationRule> due = resolver.nextDueRule(rules, 85, 80);
        assertThat(due).isEmpty();
    }

    @Test
    void nextDueRuleEmptyWhenThresholdNotReached() throws Exception {
        List<EscalationRule> rules = resolver.parseRules(rulesJson());

        Optional<EscalationRule> due = resolver.nextDueRule(rules, 30, 0);
        assertThat(due).isEmpty();
    }
}
