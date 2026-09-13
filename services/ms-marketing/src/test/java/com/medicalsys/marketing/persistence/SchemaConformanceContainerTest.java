package com.medicalsys.marketing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.marketing.support.AbstractMySqlContainerTest;
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
 * Conformidad de {@code marketing_db} con la seccion 5.12 del documento de descomposicion.
 *
 * <p>Esta clase es la evidencia automatizada de la promesa de privacidad. La seccion 5.12 la enuncia
 * en tres hechos verificables, y aqui hay una prueba por cada uno:
 * <ol>
 *   <li>"La base de ms-marketing no contiene ni una tabla clinica" &rarr;
 *       {@link #containsNoClinicalTable(String)}.</li>
 *   <li>"Su ref_paciente no tiene ninguna columna cuyo nombre contenga cie10 ni diagnostico" &rarr;
 *       {@link #containsNoClinicalColumn()}.</li>
 *   <li>"El CHECK se conserva como segunda barrera declarativa" &rarr;
 *       {@link #privacyCheckIsEnforcedByTheEngine()}, que ademas comprueba que el motor lo aplica de
 *       verdad y no solo que el texto esta escrito.</li>
 * </ol>
 */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class SchemaConformanceContainerTest extends AbstractMySqlContainerTest {

    /** Las nueve tablas propias de la seccion 5.12, las tres replicas y el par outbox/inbox. */
    private static final List<String> EXPECTED_TABLES = List.of(
            "ref_organization", "ref_patient", "ref_service_offering", "patient_activity",
            "audience_segment", "campaign", "promotion", "campaign_recipient", "promotion_redemption",
            "advertisement", "loyalty_program", "loyalty_points_movement",
            "outbox_event", "processed_event");

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("marketing_db contiene exactamente las tablas declaradas en la seccion 5.12")
    void createsExactlyTheDeclaredTables() {
        List<String> tables = jdbc().sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
                          AND table_name <> 'flyway_schema_history'
                        ORDER BY table_name
                        """)
                .query(String.class).list();

        assertThat(tables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    @ParameterizedTest(name = "marketing_db no contiene la tabla clinica {0}")
    @ValueSource(strings = {"medical_record", "clinical_encounter", "diagnosis", "allergy", "prescription",
            "study_order", "study_result", "ref_diagnosis", "ref_medical_record", "ref_clinical_encounter",
            "expediente", "encuentro_clinico", "diagnostico"})
    @DisplayName("hecho 1 de la seccion 5.12: la base no contiene ninguna tabla clinica")
    void containsNoClinicalTable(String clinicalTable) {
        Integer found = jdbc().sql("""
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name = :table
                        """)
                .param("table", clinicalTable)
                .query(Integer.class).single();

        assertThat(found).isZero();
    }

    @Test
    @DisplayName("hecho 2 de la seccion 5.12: ninguna columna nombra un dato clinico")
    void containsNoClinicalColumn() {
        List<String> suspicious = jdbc().sql("""
                        SELECT CONCAT(table_name, '.', column_name) FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND (column_name LIKE '%cie10%'    OR column_name LIKE '%icd10%'
                            OR column_name LIKE '%diagnos%'  OR column_name LIKE '%narrative%'
                            OR column_name LIKE '%anamnes%'  OR column_name LIKE '%symptom%'
                            OR column_name LIKE '%allerg%'   OR column_name LIKE '%alergi%'
                            OR column_name LIKE '%treatment%' OR column_name LIKE '%tratamiento%'
                            OR column_name LIKE '%prescription%' OR column_name LIKE '%medication%'
                            OR column_name LIKE '%encrypted%')
                        """)
                .query(String.class).list();

        assertThat(suspicious).isEmpty();
    }

    @Test
    @DisplayName("hecho 3 de la seccion 5.12: el motor rechaza uses_clinical_data = 1")
    void privacyCheckIsEnforcedByTheEngine() {
        jdbc().sql("""
                INSERT INTO ref_organization (public_id, commercial_name, includes_marketing)
                VALUES (UNHEX(REPLACE('00000000-0000-4000-8000-00000000c001', '-', '')), 'CHECK TEST', 1)
                """).update();

        // El CHECK no se comprueba leyendo su definicion sino intentando violarlo: una restriccion
        // declarada pero no aplicada por el motor no protege de nada.
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                jdbc().sql("""
                        INSERT INTO audience_segment
                            (public_id, organization_public_id, name, criteria, uses_clinical_data)
                        VALUES (UNHEX(REPLACE('00000000-0000-4000-8000-00000000c002', '-', '')),
                                UNHEX(REPLACE('00000000-0000-4000-8000-00000000c001', '-', '')),
                                'SEGMENTO PROHIBIDO', '{"minAge":30}', 1)
                        """).update()))
                .hasMessageContaining("ck_audience_segment_privacy");
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

    @ParameterizedTest(name = "{0}.{1} es {2}")
    @CsvSource({
            "ref_patient,             public_id,                       binary",
            "ref_patient,             birth_date,                      date",
            "ref_patient,             sex,                             enum",
            "ref_patient,             marketing_opt_out,               tinyint",
            "patient_activity,        total_billed_bob,                decimal",
            "patient_activity,        suggested_next_visit_on,         date",
            "audience_segment,        criteria,                        json",
            "audience_segment,        uses_clinical_data,              tinyint",
            "promotion,               discount_type,                   enum",
            "promotion,               value,                           decimal",
            "promotion,               valid_until,                     date",
            "campaign,                status,                          enum",
            "campaign,                total_converted,                 int",
            "campaign_recipient,      status,                          enum",
            "campaign_recipient,      generated_appointment_public_id, binary",
            "promotion_redemption,    discounted_amount_bob,           decimal",
            "advertisement,           placement,                       enum",
            "advertisement,           target_audience,                 json",
            "loyalty_program,         points_per_bob,                  decimal",
            "loyalty_points_movement, points,                          int",
            "loyalty_points_movement, source_event_public_id,          binary",
            "outbox_event,            payload,                         json",
            "processed_event,         event_public_id,                 binary"
    })
    @DisplayName("los tipos fisicos coinciden con el esquema declarado")
    void columnTypesMatchSchema(String table, String column, String expectedType) {
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
            "uq_audience_segment_organization_public_id_name",
            "uq_promotion_organization_public_id_coupon_code",
            "uq_campaign_recipient_campaign_id_patient_public_id",
            "uq_promotion_redemption_promotion_id_invoice_public_id",
            "uq_loyalty_points_movement_source_event_public_id",
            "uq_outbox_event_event_public_id"})
    @DisplayName("las restricciones que garantizan idempotencia y unicidad estan creadas")
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

    @ParameterizedTest(name = "la vista {0} existe")
    @ValueSource(strings = {"v_loyalty_balance", "v_campaign_performance"})
    @DisplayName("las dos vistas del esquema estan creadas y son consultables")
    void viewsExist(String viewName) {
        Integer found = jdbc().sql("""
                        SELECT COUNT(*) FROM information_schema.views
                        WHERE table_schema = DATABASE() AND table_name = :view
                        """)
                .param("view", viewName)
                .query(Integer.class).single();

        assertThat(found).isOne();
    }

    @Test
    @DisplayName("la proyeccion comercial se identifica por paciente y organizacion")
    void patientActivityIsKeyedByPatientAndOrganization() {
        List<String> key = jdbc().sql("""
                        SELECT column_name FROM information_schema.key_column_usage
                        WHERE table_schema = DATABASE() AND table_name = 'patient_activity'
                          AND constraint_name = 'PRIMARY'
                        ORDER BY ordinal_position
                        """)
                .query(String.class).list();

        assertThat(key).containsExactly("patient_public_id", "organization_public_id");
    }

    private JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }
}
