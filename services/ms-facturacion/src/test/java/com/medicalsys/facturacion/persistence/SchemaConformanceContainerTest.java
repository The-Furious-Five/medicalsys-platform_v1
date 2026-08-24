package com.medicalsys.facturacion.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.facturacion.support.AbstractMySqlContainerTest;
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
 * Conformidad del esquema con MedicalSys_Diccionario_Datos_Microservicios_MVP.pdf, capitulo 11.
 *
 * <p>Contrasta el esquema realmente creado por Flyway contra lo que dice el diccionario, incluida la
 * verificacion del Excel: {@code billing_db} no contiene ninguna tabla clinica.
 */
@SpringBootTest
@EnabledIf(AbstractMySqlContainerTest.DOCKER_CONDITION)
class SchemaConformanceContainerTest extends AbstractMySqlContainerTest {

    /** Alcance fiscal reducido del MVP; las cinco tablas restantes del capitulo 11 llegan en V2. */
    private static final List<String> MVP_TABLES = List.of(
            "billing_configuration", "point_of_sale", "cuis", "cufd", "document_sequence",
            "invoice", "invoice_item");

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("billing_db contiene exactamente las siete tablas del alcance fiscal reducido")
    void createsOnlyTheReducedFiscalTables() {
        List<String> tables = jdbc().sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                        ORDER BY table_name
                        """)
                .query(String.class).list();

        assertThat(tables).containsExactlyInAnyOrderElementsOf(MVP_TABLES);
    }

    @ParameterizedTest(name = "billing_db no contiene la tabla clinica {0}")
    @ValueSource(strings = {"medical_record", "clinical_encounter", "diagnosis", "allergy", "prescription",
            "study_order", "study_result", "patient", "appointment"})
    @DisplayName("la base fiscal no contiene ninguna tabla clinica")
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
    @DisplayName("ninguna columna guarda narrativa clinica: no hay campos de texto libre del expediente")
    void containsNoClinicalNarrativeColumn() {
        List<String> suspicious = jdbc().sql("""
                        SELECT CONCAT(table_name, '.', column_name) FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND (column_name LIKE '%narrative%' OR column_name LIKE '%diagnos%'
                               OR column_name LIKE '%anamnes%' OR column_name LIKE '%symptom%'
                               OR column_name LIKE '%allerg%' OR column_name LIKE '%treatment%')
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

    @ParameterizedTest(name = "{0}.{1} es {2}")
    @CsvSource({
            "invoice,               public_id,               binary",
            "invoice,               organization_public_id,  binary",
            "invoice,               patient_public_id,       binary",
            "invoice,               invoice_number,          bigint",
            "invoice,               cuf,                     varchar",
            "invoice,               total_amount,            decimal",
            "invoice,               exchange_rate,           decimal",
            "invoice,               status,                  enum",
            "invoice,               tax_document_type,       enum",
            "invoice,               issued_at,               timestamp",
            "invoice,               xml_hash,                char",
            "invoice_item,          quantity,                decimal",
            "invoice_item,          subtotal,                decimal",
            "invoice_item,          item_number,             smallint",
            "invoice_item,          appointment_public_id,   binary",
            "document_sequence,     last_number,             bigint",
            "document_sequence,     tax_document_type,       varchar",
            "point_of_sale,         tax_branch_code,         smallint",
            "billing_configuration, encrypted_delegated_token, varbinary",
            "cufd,                  valid_until,             timestamp"
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
            "uq_invoice_public_id",
            "uq_invoice_cuf",
            "uq_invoice_point_of_sale_id_tax_document_type_invoice_number",
            "uq_invoice_item_invoice_id_item_number",
            "uq_billing_configuration_organization_public_id",
            "uq_cufd_code"})
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

    @Test
    @DisplayName("el correlativo tiene clave compuesta por punto de venta y tipo de documento")
    void documentSequenceIsKeyedByPointOfSaleAndType() {
        List<String> key = jdbc().sql("""
                        SELECT column_name FROM information_schema.key_column_usage
                        WHERE table_schema = DATABASE() AND table_name = 'document_sequence'
                          AND constraint_name = 'PRIMARY'
                        ORDER BY ordinal_position
                        """)
                .query(String.class).list();

        assertThat(key).containsExactly("point_of_sale_id", "tax_document_type");
    }

    private JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }
}
