package com.medicalsys.historial.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.historial.support.AbstractMySqlContainerTest;
import java.util.List;
import java.util.Map;
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
 * Conformidad del esquema con MedicalSys_Diccionario_Datos_Microservicios_MVP.pdf, capitulo 9.
 *
 * <p>Sustituye a la validacion de Hibernate, que produce falsos positivos con ENUM y BINARY(16) de
 * MySQL: aqui se contrasta el esquema realmente creado por Flyway contra lo que dice el diccionario.
 */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class SchemaConformanceContainerTest extends AbstractMySqlContainerTest {

    private static final List<String> DICTIONARY_TABLES = List.of(
            "medical_record", "allergy", "medical_background", "clinical_encounter", "encounter_addendum",
            "vital_sign", "diagnosis", "prescription", "prescription_item", "study_order", "study_result",
            "performed_procedure");

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("medical_records_db contiene exactamente las doce tablas del diccionario")
    void createsTwelveDictionaryTables() {
        List<String> tables = jdbc().sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                        ORDER BY table_name
                        """)
                .query(String.class).list();

        assertThat(tables).containsExactlyInAnyOrderElementsOf(DICTIONARY_TABLES);
    }

    @ParameterizedTest(name = "{0}.{1} es {2}")
    @CsvSource({
            "medical_record,      public_id,                binary",
            "medical_record,      organization_public_id,   binary",
            "medical_record,      record_number,            varchar",
            "medical_record,      opened_on,                date",
            "medical_record,      status,                   enum",
            "clinical_encounter,  encrypted,                varbinary",
            "clinical_encounter,  encrypted_subjective_notes, varbinary",
            "clinical_encounter,  content_hash,             char",
            "clinical_encounter,  status,                   enum",
            "clinical_encounter,  starts_at,                timestamp",
            "vital_sign,          temperature_c,            decimal",
            "vital_sign,          bmi,                      decimal",
            "diagnosis,           icd10_code,               varchar",
            "study_order,         study_type_code,          varchar",
            "study_result,        document_public_id,       binary",
            "prescription_item,   quantity,                 decimal"
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

    @ParameterizedTest(name = "existe la restriccion {0}")
    @ValueSource(strings = {
            "uq_medical_record_public_id",
            "uq_medical_record_organization_public_id_record_number",
            "uq_medical_record_organization_public_id_patient_public_id",
            "uq_allergy_medical_record_id_type_substance",
            "uq_clinical_encounter_public_id",
            "uq_clinical_encounter_appointment_public_id",
            "uq_diagnosis_clinical_encounter_id_icd10_code",
            "uq_prescription_organization_public_id_folio",
            "uq_prescription_item_prescription_id_item_number",
            "uq_study_order_public_id",
            "uq_study_result_study_order_id_parameter"
    })
    @DisplayName("las restricciones de unicidad del diccionario estan creadas")
    void uniqueConstraintsExist(String constraintName) {
        Integer count = jdbc().sql("""
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_schema = DATABASE() AND constraint_name = :name
                          AND constraint_type = 'UNIQUE'
                        """)
                .param("name", constraintName)
                .query(Integer.class).single();

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("las claves foraneas son solo locales: ninguna apunta fuera de medical_records_db")
    void hasOnlyLocalForeignKeys() {
        List<Map<String, Object>> foreignKeys = jdbc().sql("""
                        SELECT table_name, referenced_table_name, referenced_table_schema
                        FROM information_schema.key_column_usage
                        WHERE table_schema = DATABASE() AND referenced_table_name IS NOT NULL
                        """)
                .query().listOfRows();

        assertThat(foreignKeys).isNotEmpty();
        assertThat(foreignKeys)
                .allSatisfy(fk -> assertThat(fk.get("REFERENCED_TABLE_SCHEMA")).isEqualTo("medical_records_db"));
        assertThat(foreignKeys)
                .allSatisfy(fk -> assertThat(DICTIONARY_TABLES)
                        .contains(String.valueOf(fk.get("REFERENCED_TABLE_NAME"))));
    }

    @Test
    @DisplayName("los identificadores externos llevan indice propio y no clave foranea")
    void externalReferencesAreIndexedWithoutForeignKey() {
        List<String> indexed = jdbc().sql("""
                        SELECT DISTINCT column_name FROM information_schema.statistics
                        WHERE table_schema = DATABASE() AND table_name = 'clinical_encounter'
                        """)
                .query(String.class).list();

        assertThat(indexed).contains("appointment_public_id", "health_professional_public_id",
                "branch_public_id", "encryption_key_public_id");

        Integer externalForeignKeys = jdbc().sql("""
                        SELECT COUNT(*) FROM information_schema.key_column_usage
                        WHERE table_schema = DATABASE() AND table_name = 'clinical_encounter'
                          AND column_name IN ('appointment_public_id', 'health_professional_public_id',
                                              'branch_public_id', 'encryption_key_public_id')
                          AND referenced_table_name IS NOT NULL
                        """)
                .query(Integer.class).single();

        assertThat(externalForeignKeys).isZero();
    }

    @Test
    @DisplayName("bmi es una columna generada por la base, no un valor enviado por la aplicacion")
    void bmiIsGenerated() {
        String extra = jdbc().sql("""
                        SELECT extra FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 'vital_sign' AND column_name = 'bmi'
                        """)
                .query(String.class).single();

        assertThat(extra).contains("STORED GENERATED");
    }

    @Test
    @DisplayName("existen los triggers de inmutabilidad clinica")
    void immutabilityTriggersExist() {
        List<String> triggers = jdbc().sql("""
                        SELECT trigger_name FROM information_schema.triggers
                        WHERE trigger_schema = DATABASE() ORDER BY trigger_name
                        """)
                .query(String.class).list();

        assertThat(triggers).contains(
                "trg_clinical_encounter_immutable_on_update",
                "trg_clinical_encounter_no_delete",
                "trg_encounter_addendum_no_update",
                "trg_encounter_addendum_no_delete");
    }

    private JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }
}
