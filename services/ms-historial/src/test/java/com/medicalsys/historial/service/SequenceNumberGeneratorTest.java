package com.medicalsys.historial.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Year;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El correlativo debe ser legible, comparable como texto y continuo. */
class SequenceNumberGeneratorTest {

    private final SequenceNumberGenerator generator = new SequenceNumberGenerator();

    @Test
    @DisplayName("el primer correlativo del anio arranca en uno")
    void startsAtOne() {
        String prefix = generator.prefixForCurrentYear("EXP");

        assertThat(generator.next(prefix, Optional.empty()))
                .isEqualTo("EXP-" + Year.now().getValue() + "-000001");
    }

    @Test
    @DisplayName("continua a partir del ultimo emitido")
    void continuesFromLastEmitted() {
        String prefix = generator.prefixForCurrentYear("REC");

        assertThat(generator.next(prefix, Optional.of(prefix + "000041"))).isEqualTo(prefix + "000042");
    }

    @Test
    @DisplayName("el relleno con ceros hace que el orden de texto coincida con el numerico")
    void keepsLexicographicOrder() {
        String prefix = generator.prefixForCurrentYear("EXP");

        assertThat(generator.next(prefix, Optional.of(prefix + "000009")))
                .isGreaterThan(prefix + "000009");
    }

    @Test
    @DisplayName("un correlativo con formato ajeno no bloquea la emision")
    void toleratesLegacyFormat() {
        String prefix = generator.prefixForCurrentYear("EXP");

        assertThat(generator.next(prefix, Optional.of(prefix + "ANTIGUO"))).isEqualTo(prefix + "000001");
    }
}
