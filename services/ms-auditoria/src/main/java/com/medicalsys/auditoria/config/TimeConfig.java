package com.medicalsys.auditoria.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj unico del servicio.
 *
 * <p>Se publica como bean en vez de llamar {@code Instant.now()} disperso por el codigo: en una
 * bitacora la marca de tiempo forma parte del hash, y una prueba tiene que poder fijarla para
 * comprobar que la cadena se calcula igual siempre.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
