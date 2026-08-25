package com.medicalsys.tenencia.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.tenencia.support.AbstractMySqlContainerTest;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Conformidad del esquema con MedicalSys_Diccionario_Datos_Microservicios_MVP.pdf, capitulo 3.
 *
 * <p>Contrasta el esquema realmente creado por Flyway contra lo que dice el diccionario, incluida la
 * verificacion del Excel: {@code tenancy_db} no contiene ninguna tabla clinica ni fiscal, y no
 * declara ninguna clave foranea hacia otra base.
 */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class SchemaConformanceContainerTest extends AbstractMySqlContainerTest {

    /** Las seis tablas del capitulo 3. */
    private static final List<String> DICTIONARY_TABLES = List.of(
            "subscription_plan", "organization", "subscription", "branch", "service_offering", "plan_usage");

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("tenancy_db contiene exactamente las seis tablas del capitulo 3")
    void createsExactlyTheDictionaryTables() {
        List<String> tables = jdbc().sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                        ORDER BY table_name
                        """)
                .query(String.class).list();

        assertThat(tables).containsExactlyInAnyOrderElementsOf(DICTIONARY_TABLES);
    }

    @ParameterizedTest(name = "tenancy_db no contiene la tabla ajena {0}")
    @ValueSource(strings = {"patient", "appointment", "invoice", "medical_record", "clinical_encounter",
            "user_account", "specialty", "document", "health_professional"})
    @DisplayName("la base de tenencia no contiene tablas de otro servicio")
    void containsNoForeignServiceTable(String foreignTable) {
        Integer found = jdbc().sql("""
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name = :table
                        """)
                .param("table", foreignTable)
                .query(Integer.class).single();

        assertThat(found).isZero();
    }

    @Test
    @DisplayName("ninguna columna guarda datos clinicos: tenencia no es propietaria de esa informacion")
    void containsNoClinicalColumn() {
        List<String> suspicious = jdbc().sql("""
                        SELECT CONCAT(table_name, '.', column_name) FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND (column_name LIKE '%narrative%' OR column_name LIKE '%diagnos%'
                               OR column_name LIKE '%allerg%' OR column_name LIKE '%prescription%'
                               OR column_name LIKE '%patient%' OR column_name LIKE '%appointment%')
                        """)
                .query(String.class).list();

        assertThat(suspicious).isEmpty();
    }

    @Test
    @DisplayName("no existe ninguna clave foranea hacia otra base")
    void declaresOnlyLocalForeignKeys() {
        List<String> external = jdbc().sql("""
                        SELECT CONCAT(table_name, '.', column_name, ' -> ', referenced_table_schema)
                        FROM information_schema.key_column_usage
                        WHERE table_schema = DATABASE()
                          AND referenced_table_schema IS NOT NULL
                          AND referenced_table_schema <> DATABASE()
                        """)
                .query(String.class).list();

        assertThat(external).isEmpty();
    }

    @Test
    @DisplayName("las referencias externas del capitulo 12 existen como columnas sin clave foranea")
    void externalReferencesHaveNoForeignKey() {
        List<String> constrained = jdbc().sql("""
                        SELECT column_name FROM information_schema.key_column_usage
                        WHERE table_schema = DATABASE() AND table_name = 'service_offering'
                          AND column_name IN ('specialty_code', 'consent_template_public_id')
                          AND referenced_table_name IS NOT NULL
                        """)
                .query(String.class).list();

        assertThat(constrained).isEmpty();
    }

    @ParameterizedTest(name = "{0}.{1} es {2}")
    @CsvSource({
            "subscription_plan, id,                          smallint",
            "subscription_plan, code,                        varchar",
            "subscription_plan, monthly_price_bob,           decimal",
            "subscription_plan, max_users,                   smallint",
            "subscription_plan, features,                    json",
            "subscription_plan, active,                      tinyint",
            "subscription_plan, created_at,                  timestamp",
            "organization,      id,                          bigint",
            "organization,      public_id,                   binary",
            "organization,      code,                        varchar",
            "organization,      nit,                         varchar",
            "organization,      currency,                    char",
            "organization,      logo_url,                    varchar",
            "organization,      updated_at,                  timestamp",
            "subscription,      organization_id,             bigint",
            "subscription,      subscription_plan_id,        smallint",
            "subscription,      start_date,                  date",
            "subscription,      end_date,                    date",
            "subscription,      agreed_price_bob,            decimal",
            "branch,            public_id,                   binary",
            "branch,            is_headquarters,             tinyint",
            "branch,            latitude,                    decimal",
            "branch,            longitude,                   decimal",
            "branch,            opening_hours,               json",
            "service_offering,  public_id,                   binary",
            "service_offering,  specialty_code,              varchar",
            "service_offering,  duration_minutes,            smallint",
            "service_offering,  base_price_bob,              decimal",
            "service_offering,  consent_template_public_id,  binary",
            "service_offering,  tax_product_code,            varchar",
            "service_offering,  tax_unit_code,               varchar",
            "plan_usage,        current_value,               bigint",
            "plan_usage,        period_start,                date",
            "plan_usage,        version,                     bigint",
            "plan_usage,        updated_at,                  timestamp"
    })
    @DisplayName("los tipos fisicos coinciden con el diccionario")
    void columnTypesMatchDictionary(String table, String column, String expectedType) {
        String actual = jdbc().sql("""
                        SELECT data_type FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = :table AND column_name = :column
                        """)
                .param("table", table).param("column", column)
                .query(String.class).single();

        assertThat(actual).isEqualTo(expectedType);
    }

    @ParameterizedTest(name = "existe la restriccion de unicidad {0}")
    @ValueSource(strings = {
            "uq_subscription_plan_code",
            "uq_organization_public_id",
            "uq_organization_code",
            "uq_organization_nit",
            "uq_branch_public_id",
            "uq_branch_organization_id_code",
            "uq_service_offering_public_id",
            "uq_service_offering_organization_id_code",
            "uq_plan_usage_organization_id_metric_period_start"})
    @DisplayName("las restricciones de unicidad del diccionario estan creadas")
    void uniqueConstraintsExist(String constraintName) {
        Integer found = jdbc().sql("""
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_schema = DATABASE() AND constraint_name = :name
                          AND constraint_type = 'UNIQUE'
                        """)
                .param("name", constraintName)
                .query(Integer.class).single();

        assertThat(found).isOne();
    }

    @ParameterizedTest(name = "existe la clave foranea local {0}")
    @ValueSource(strings = {
            "fk_subscription_organization",
            "fk_subscription_subscription_plan",
            "fk_branch_organization",
            "fk_service_offering_organization",
            "fk_plan_usage_organization"})
    @DisplayName("las claves foraneas locales del diccionario estan creadas")
    void localForeignKeysExist(String constraintName) {
        Integer found = jdbc().sql("""
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_schema = DATABASE() AND constraint_name = :name
                          AND constraint_type = 'FOREIGN KEY'
                        """)
                .param("name", constraintName)
                .query(Integer.class).single();

        assertThat(found).isOne();
    }

    @ParameterizedTest(name = "existe el CHECK de dominio {0}")
    @ValueSource(strings = {
            "ck_subscription_plan_monthly_price_bob",
            "ck_organization_type",
            "ck_organization_department",
            "ck_organization_status",
            "ck_subscription_billing_cycle",
            "ck_subscription_status",
            "ck_subscription_validity",
            "ck_branch_department",
            "ck_branch_status",
            "ck_service_offering_type",
            "ck_service_offering_required_resource_type",
            "ck_service_offering_duration_minutes",
            "ck_service_offering_base_price_bob",
            "ck_plan_usage_metric",
            "ck_plan_usage_current_value",
            "ck_plan_usage_period"})
    @DisplayName("los valores controlados y las reglas de dominio se aplican con CHECK")
    void checkConstraintsExist(String constraintName) {
        Integer found = jdbc().sql("""
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_schema = DATABASE() AND constraint_name = :name
                          AND constraint_type = 'CHECK'
                        """)
                .param("name", constraintName)
                .query(Integer.class).single();

        assertThat(found).isOne();
    }

    @ParameterizedTest(name = "existe el indice {0}")
    @ValueSource(strings = {
            "ix_organization_status",
            "ix_subscription_organization_id_status",
            "ix_subscription_subscription_plan_id",
            "ix_branch_organization_id_status",
            "ix_service_offering_organization_id_active",
            "ix_service_offering_specialty_code",
            "ix_plan_usage_organization_id"})
    @DisplayName("los indices de consulta del diccionario estan creados")
    void indexesExist(String indexName) {
        Integer found = jdbc().sql("""
                        SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                        WHERE table_schema = DATABASE() AND index_name = :name
                        """)
                .param("name", indexName)
                .query(Integer.class).single();

        assertThat(found).isOne();
    }

    private JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }
}
