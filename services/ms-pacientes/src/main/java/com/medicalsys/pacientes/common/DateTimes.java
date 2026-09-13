package com.medicalsys.pacientes.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Conversion entre el instante UTC persistido y la representacion ISO-8601 con zona del contrato.
 *
 * <p>La base guarda UTC; la API expone offset explicito para que ningun consumidor tenga que
 * suponer la zona.
 */
public final class DateTimes {

    private DateTimes() {
    }

    public static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.toInstant();
    }
}
