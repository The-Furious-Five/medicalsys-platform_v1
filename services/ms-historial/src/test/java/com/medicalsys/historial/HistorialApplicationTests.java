package com.medicalsys.historial;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.historial.service.DemoMedicalRecordSeeder;
import com.medicalsys.historial.support.AbstractMySqlContainerTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Prueba de contexto, health y migracion exigida por la plantilla comun del sprint.
 */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class HistorialApplicationTests extends AbstractMySqlContainerTest {

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DemoMedicalRecordSeeder seeder;

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

    /**
     * La semilla de demostracion dejo de ser SQL y pasó a {@link DemoMedicalRecordSeeder}, porque
     * la narrativa clinica se persiste cifrada y un script no puede producir ese ciphertext.
     *
     * <p>Por eso la prueba ya no busca la fila 'EXP-DEMO-000001' que creaba la migracion antigua:
     * ejecuta el sembrador dos veces y comprueba que la segunda no anade nada. Es la propiedad
     * que de verdad importa, y ademas no puede cumplirse por casualidad: la tabla
     * {@code clinical_encounter} tiene un trigger que impide borrar, asi que una siembra no
     * idempotente dejaria expedientes duplicados imposibles de limpiar.
     */
    @Test
    @DisplayName("la semilla de demostracion es idempotente")
    void demoSeedIsIdempotent() {
        seeder.run(new DefaultApplicationArguments());
        int afterFirstRun = countRecords();

        seeder.run(new DefaultApplicationArguments());

        assertThat(afterFirstRun).isPositive();
        assertThat(countRecords()).isEqualTo(afterFirstRun);
    }

    private int countRecords() {
        return JdbcClient.create(dataSource)
                .sql("SELECT COUNT(*) FROM medical_record")
                .query(Integer.class)
                .single();
    }
}
