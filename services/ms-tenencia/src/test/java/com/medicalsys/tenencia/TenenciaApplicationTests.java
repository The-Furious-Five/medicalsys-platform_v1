package com.medicalsys.tenencia;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.tenencia.support.AbstractMySqlContainerTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Prueba de contexto, health y migracion exigida por la plantilla comun del sprint. */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class TenenciaApplicationTests extends AbstractMySqlContainerTest {

	@Autowired
	private HealthEndpoint healthEndpoint;

	@Autowired
	private DataSource dataSource;

	@Test
	@DisplayName("el contexto arranca y el health refleja la dependencia de base")
	void contextLoadsAndHealthIsUp() {
		assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	@DisplayName("Flyway aplica las migraciones desde una base vacia")
	void migrationsAreApplied() {
		var applied = JdbcClient.create(dataSource)
				.sql("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank")
				.query(String.class)
				.list();

		assertThat(applied).contains("1", "2");
	}
}
