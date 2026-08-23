package com.medicalsys.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.agenda.domain.ScheduleAvailability;
import com.medicalsys.agenda.domain.ScheduleBlock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Vigencia de las reglas semanales y solapamiento de bloqueos. */
class ScheduleAvailabilityTests {

    private ScheduleAvailability weeklyRule(short dayOfWeek, LocalDate from, LocalDate until, boolean active) {
        ScheduleAvailability availability = new ScheduleAvailability();
        availability.setDayOfWeek(dayOfWeek);
        availability.setValidFrom(from);
        availability.setValidUntil(until);
        availability.setActive(active);
        return availability;
    }

    @Test
    @DisplayName("la regla aplica solo al dia de la semana declarado")
    void appliesOnlyToItsWeekday() {
        // 2026-08-24 es lunes (ISO-8601: 1).
        ScheduleAvailability monday = weeklyRule((short) 1, LocalDate.of(2026, 1, 1), null, true);

        assertThat(monday.coversDate(LocalDate.of(2026, 8, 24))).isTrue();
        assertThat(monday.coversDate(LocalDate.of(2026, 8, 25))).isFalse();
    }

    @Test
    @DisplayName("una regla vencida o desactivada deja de generar huecos")
    void expiredOrInactiveRuleDoesNotApply() {
        ScheduleAvailability expired =
                weeklyRule((short) 1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), true);
        ScheduleAvailability inactive = weeklyRule((short) 1, LocalDate.of(2026, 1, 1), null, false);

        assertThat(expired.coversDate(LocalDate.of(2026, 8, 24))).isFalse();
        assertThat(inactive.coversDate(LocalDate.of(2026, 8, 24))).isFalse();
    }

    @Test
    @DisplayName("un bloqueo solapa por interseccion abierta, no por tocarse en el borde")
    void blockOverlapIsHalfOpen() {
        ScheduleBlock block = new ScheduleBlock();
        block.setStartsAt(OffsetDateTime.of(2026, 8, 24, 10, 0, 0, 0, ZoneOffset.UTC));
        block.setEndsAt(OffsetDateTime.of(2026, 8, 24, 12, 0, 0, 0, ZoneOffset.UTC));

        OffsetDateTime nineToTenStart = OffsetDateTime.of(2026, 8, 24, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime nineToTenEnd = OffsetDateTime.of(2026, 8, 24, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime elevenToTwelveStart = OffsetDateTime.of(2026, 8, 24, 11, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime elevenToTwelveEnd = OffsetDateTime.of(2026, 8, 24, 12, 0, 0, 0, ZoneOffset.UTC);

        assertThat(block.overlaps(nineToTenStart, nineToTenEnd)).isFalse();
        assertThat(block.overlaps(elevenToTwelveStart, elevenToTwelveEnd)).isTrue();
    }
}
