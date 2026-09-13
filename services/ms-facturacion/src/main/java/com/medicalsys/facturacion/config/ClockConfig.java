package com.medicalsys.facturacion.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del servicio.
 *
 * <p>Se inyecta en vez de llamar a {@code Instant.now()} para que las pruebas puedan fijar el
 * instante de emision y comprobar que el CUF simulado es determinista.
 */
@Configuration
public class ClockConfig {

    /** Reloj UTC: la base guarda instantes en UTC y la API expone el offset explicito. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
