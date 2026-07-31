package ru.expertise.workflow.sla;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Demo-scale business calendar: Mon-Fri, 09:00-18:00, no public holidays. Used to spread SLA
 * durations across working time only (REQ-02-005 "Контроль SLA с календарями").
 */
public class BusinessCalendar {

    private static final int BUSINESS_START_HOUR = 9;
    private static final int BUSINESS_END_HOUR = 18;

    private final ZoneId zone;

    public BusinessCalendar(ZoneId zone) {
        this.zone = zone;
    }

    public Instant addBusinessMinutes(Instant start, long minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("minutes must not be negative");
        }
        ZonedDateTime cursor = alignToBusinessMoment(start.atZone(zone));
        long remaining = minutes;

        while (remaining > 0) {
            ZonedDateTime endOfDay = cursor.withHour(BUSINESS_END_HOUR).withMinute(0).withSecond(0).withNano(0);
            long minutesLeftToday = Duration.between(cursor, endOfDay).toMinutes();

            if (minutesLeftToday <= 0) {
                cursor = nextBusinessDayStart(cursor);
                continue;
            }
            if (remaining <= minutesLeftToday) {
                cursor = cursor.plusMinutes(remaining);
                remaining = 0;
            } else {
                remaining -= minutesLeftToday;
                cursor = nextBusinessDayStart(cursor);
            }
        }
        return cursor.toInstant();
    }

    private ZonedDateTime alignToBusinessMoment(ZonedDateTime moment) {
        ZonedDateTime aligned = moment;
        if (isWeekend(aligned)) {
            return nextBusinessDayStart(aligned.minusDays(1));
        }
        ZonedDateTime startOfDay = aligned.withHour(BUSINESS_START_HOUR).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime endOfDay = aligned.withHour(BUSINESS_END_HOUR).withMinute(0).withSecond(0).withNano(0);
        if (aligned.isBefore(startOfDay)) {
            return startOfDay;
        }
        if (!aligned.isBefore(endOfDay)) {
            return nextBusinessDayStart(aligned);
        }
        return aligned;
    }

    private ZonedDateTime nextBusinessDayStart(ZonedDateTime from) {
        ZonedDateTime next = from.plusDays(1).withHour(BUSINESS_START_HOUR).withMinute(0).withSecond(0).withNano(0);
        while (isWeekend(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    private boolean isWeekend(ZonedDateTime dt) {
        return dt.getDayOfWeek() == DayOfWeek.SATURDAY || dt.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
