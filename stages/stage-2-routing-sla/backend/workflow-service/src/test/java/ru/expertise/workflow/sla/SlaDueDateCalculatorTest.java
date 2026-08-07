package ru.expertise.workflow.sla;

import org.junit.jupiter.api.Test;
import ru.expertise.workflow.domain.SlaPolicy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SlaDueDateCalculatorTest {

    private final SlaDueDateCalculator calculator =
            new SlaDueDateCalculator("UTC", new ru.expertise.workflow.config.WorkflowProperties());

    private Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("UTC")).toInstant();
    }

    @Test
    void businessHoursPolicyUsesCalendar() {
        SlaPolicy policy = new SlaPolicy();
        policy.setDurationMinutes(120);
        policy.setBusinessHoursOnly(true);

        Instant from = at(2026, 7, 27, 17, 0); // Monday 17:00, 60 min left today
        Instant due = calculator.computeDueAt(policy, from);

        assertThat(due).isEqualTo(at(2026, 7, 28, 10, 0));
    }

    @Test
    void nonBusinessHoursPolicyIsPlainDuration() {
        SlaPolicy policy = new SlaPolicy();
        policy.setDurationMinutes(180);
        policy.setBusinessHoursOnly(false);

        Instant from = at(2026, 7, 27, 17, 0);
        Instant due = calculator.computeDueAt(policy, from);

        assertThat(due).isEqualTo(at(2026, 7, 27, 20, 0));
    }
}
