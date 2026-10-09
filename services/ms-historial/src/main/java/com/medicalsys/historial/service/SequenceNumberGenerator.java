package com.medicalsys.historial.service;

import java.time.Year;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Genera correlativos legibles por tenant con el formato {@code PREFIJO-AAAA-NNNNNN}.
 *
 * <p>El relleno con ceros de ancho fijo permite obtener el ultimo emitido con un {@code MAX} sobre
 * el texto. La unicidad real la garantiza la restriccion UNIQUE de la base; este generador solo
 * propone el siguiente valor y el servicio reintenta si dos transacciones concurrentes coinciden.
 */
@Component
public class SequenceNumberGenerator {

    private static final int SEQUENCE_WIDTH = 6;
    private static final String SEPARATOR = "-";

    /** Prefijo del anio en curso, por ejemplo {@code EXP-2026-}. */
    public String prefixForCurrentYear(String prefix) {
        return prefix + SEPARATOR + Year.now().getValue() + SEPARATOR;
    }

    /**
     * Calcula el siguiente correlativo.
     *
     * @param yearPrefix  prefijo devuelto por {@link #prefixForCurrentYear(String)}
     * @param lastEmitted ultimo correlativo emitido para ese prefijo, si existe
     */
    public String next(String yearPrefix, Optional<String> lastEmitted) {
        long nextSequence = lastEmitted
                .map(value -> value.substring(yearPrefix.length()))
                .map(SequenceNumberGenerator::parseSequence)
                .orElse(0L) + 1;
        return yearPrefix + String.format("%0" + SEQUENCE_WIDTH + "d", nextSequence);
    }

    private static long parseSequence(String suffix) {
        try {
            return Long.parseLong(suffix);
        } catch (NumberFormatException ignored) {
            // Un correlativo con formato antiguo no debe bloquear la emision del siguiente.
            return 0L;
        }
    }
}
