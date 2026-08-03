package ru.expertise.workflow.sla;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessCalendarTest {

    private final ZoneId zone = ZoneId.of("UTC");
    private final BusinessCalendar calendar = new BusinessCalendar(zone);

    private Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant();
    }

    @Test
    void staysWithinSameBusinessDayWhenEnoughTimeLeft() {
        Instant start = at(2026, 7, 27, 10, 0); // Monday 10:00
        Instant result = calendar.addBusinessMinutes(start, 60);
        assertThat(result).isEqualTo(at(2026, 7, 27, 11, 0));
    }

    @Test
    void rollsOverToNextBusinessDayPastEndOfDay() {
        Instant start = at(2026, 7, 27, 17, 30); // Monday 17:30, 30 min left before 18:00
        Instant result = calendar.addBusinessMinutes(start, 90); // 30 today + 60 tomorrow
        assertThat(result).isEqualTo(at(2026, 7, 28, 10, 0));
    }

    @Test
    void skipsWeekend() {
        Instant start = at(2026, 7, 31, 17, 30); // Friday 17:30
        Instant result = calendar.addBusinessMinutes(start, 90); // 30 Fri + 60 Monday
        assertThat(result).isEqualTo(at(2026, 8, 3, 10, 0)); // Monday
    }

    @Test
    void alignsFromOutsideBusinessHoursToNextStart() {
        Instant start = at(2026, 7, 27, 22, 0); // Monday night
        Instant result = calendar.addBusinessMinutes(start, 30);
        assertThat(result).isEqualTo(at(2026, 7, 28, 9, 30));
    }

    @Test
    void alignsFromWeekendToMondayStart() {
        Instant start = at(2026, 8, 1, 12, 0); // Saturday noon
        Instant result = calendar.addBusinessMinutes(start, 30);
        assertThat(result).isEqualTo(at(2026, 8, 3, 9, 30));
    }

    @Test
    void skipsConfiguredHolidays() {
        BusinessCalendar withHolidays = new BusinessCalendar(ZoneId.of("UTC"),
                java.util.List.of(java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 2)));

        // 31 Dec 2026-... is a Thursday 17:00; one working hour left, then two holidays and a weekend.
        Instant start = ZonedDateTime.of(2025, 12, 31, 17, 0, 0, 0, ZoneId.of("UTC")).toInstant();
        Instant due = withHolidays.addBusinessMinutes(start, 120);

        assertThat(due.atZone(ZoneId.of("UTC")).toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 1, 5));
    }
}
