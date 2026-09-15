package com.medicalsys.tenencia.infrastructure.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ejecuta una consulta minima y de solo lectura para mantener activa la conexion
 * con la base administrada durante las demostraciones del MVP.
 *
 * <p>Existe una sola instancia de este componente, en ms-tenencia, para evitar
 * que cada microservicio genere su propio trafico periodico. Un fallo se registra
 * y se reintenta en el siguiente ciclo; nunca detiene el servicio.
 */
@Component
@ConditionalOnProperty(
        prefix = "medicalsys.database.keep-alive",
        name = "enabled",
        havingValue = "true")
public class DatabaseKeepAlive {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseKeepAlive.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseKeepAlive(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(
            initialDelayString = "${medicalsys.database.keep-alive.initial-delay:PT1M}",
            fixedDelayString = "${medicalsys.database.keep-alive.interval:PT10M}")
    public void ping() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            LOGGER.debug("Database keep-alive completed");
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Database keep-alive failed; it will retry on the next interval ({})",
                    exception.getClass().getSimpleName());
        }
    }
}
