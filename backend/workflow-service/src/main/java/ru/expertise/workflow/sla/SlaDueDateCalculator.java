package ru.expertise.workflow.sla;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.expertise.workflow.config.WorkflowProperties;
import ru.expertise.workflow.domain.SlaPolicy;

import java.time.Instant;
import java.time.ZoneId;

@Component
public class SlaDueDateCalculator {

    private final BusinessCalendar businessCalendar;

    public SlaDueDateCalculator(@Value("${workflow.sla.zone:UTC}") String zoneId, WorkflowProperties properties) {
        this.businessCalendar = new BusinessCalendar(ZoneId.of(zoneId), properties.getSla().getHolidays());
    }

    public Instant computeDueAt(SlaPolicy policy, Instant from) {
        if (policy.isBusinessHoursOnly()) {
            return businessCalendar.addBusinessMinutes(from, policy.getDurationMinutes());
        }
        return from.plusSeconds(policy.getDurationMinutes() * 60L);
    }
}
