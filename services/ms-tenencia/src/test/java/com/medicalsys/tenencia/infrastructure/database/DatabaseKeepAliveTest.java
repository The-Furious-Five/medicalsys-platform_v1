package com.medicalsys.tenencia.infrastructure.database;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseKeepAliveTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DatabaseKeepAlive keepAlive = new DatabaseKeepAlive(jdbcTemplate);

    @Test
    void executesMinimalReadOnlyQuery() {
        keepAlive.ping();

        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }

    @Test
    void databaseFailureDoesNotEscapeScheduledExecution() {
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(jdbcTemplate)
                .queryForObject("SELECT 1", Integer.class);

        keepAlive.ping();

        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }
}
