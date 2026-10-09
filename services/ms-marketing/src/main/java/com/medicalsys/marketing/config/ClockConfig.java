package com.medicalsys.marketing.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del servicio.
 *
 * <p>Se inyecta en vez de llamar a {@code Instant.now()} para que las pruebas puedan fijar el
 * instante y comprobar sin ambiguedad la vigencia de un cupon, la ventana de un anuncio o la edad
 * con la que un paciente entra en un segmento.
 */
@Configuration
public class ClockConfig {

    /** Reloj UTC: la base guarda instantes en UTC y la API expone el offset explicito. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
