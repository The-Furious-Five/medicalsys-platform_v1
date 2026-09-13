package com.medicalsys.tenencia.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del servicio.
 *
 * <p>Se inyecta en vez de llamar a {@code LocalDate.now()} para que las pruebas puedan fijar el dia
 * y comprobar la vigencia de una suscripcion sin depender de la fecha real de ejecucion.
 */
@Configuration
public class ClockConfig {

    /** Reloj UTC: la base guarda instantes en UTC y la API expone el offset explicito. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
