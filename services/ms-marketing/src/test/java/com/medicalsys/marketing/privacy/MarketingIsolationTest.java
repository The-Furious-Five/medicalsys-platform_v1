package com.medicalsys.marketing.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprueba el aislamiento estructural que la seccion 5.12 declara: ms-marketing no puede alcanzar
 * ms-historial.
 *
 * <p>Es una prueba sobre el codigo fuente y no sobre el comportamiento, y esa rareza esta
 * justificada. Las otras dos barreras -- que la base no tenga tablas clinicas y que la red no ofrezca
 * ruta -- se comprueban con el esquema y con Compose. La tercera, "ms-marketing no tiene sus
 * credenciales", solo se puede romper anadiendo un cliente o una propiedad, y eso es exactamente lo
 * que esta prueba vigila: falla el dia que alguien escriba en este modulo la primera referencia a
 * ms-historial.
 */
class MarketingIsolationTest {

    /** Raiz del codigo del servicio. */
    private static final Path SOURCE_ROOT = Path.of("src", "main");

    /**
     * Cadenas que solo aparecen si alguien intento alcanzar de verdad el expediente.
     *
     * <p>Son direcciones, credenciales y nombres de bean, no palabras sueltas: el codigo <em>habla</em>
     * de ms-historial en sus comentarios -- explicar por que no se le consulta es parte de la
     * documentacion -- y una lista mas laxa convertiria esta prueba en un ruido que alguien acabaria
     * silenciando.
     */
    private static final List<String> FORBIDDEN_REFERENCES = List.of(
            "http://ms-historial", "https://ms-historial", "ms-historial:8088",
            "/medical_records_db", "medical_records_user", "medical_records_password",
            "medical_records_base_url", "medical_records_service_url",
            "medicalrecordsclient", "historialclient", "medicalrecordsproperties");

    @Test
    @DisplayName("ningun archivo del servicio referencia a ms-historial ni a su base")
    void doesNotReferenceMedicalRecordsService() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            offenders = files
                    .filter(Files::isRegularFile)
                    .filter(MarketingIsolationTest::mentionsForbiddenReference)
                    // Esta misma prueba nombra lo prohibido; se excluye para no acusarse a si misma.
                    .filter(path -> !path.toString().contains("MarketingIsolationTest"))
                    .map(Path::toString)
                    .toList();
        }

        assertThat(offenders)
                .as("ms-marketing no debe tener credenciales, cliente ni configuracion hacia ms-historial")
                .isEmpty();
    }

    @Test
    @DisplayName("la consulta de audiencia solo lee ref_patient y patient_activity")
    void audienceQueryReadsOnlyTwoTables() throws IOException {
        String source = Files.readString(
                SOURCE_ROOT.resolve("java/com/medicalsys/marketing/repository/AudienceQueryRepository.java"),
                StandardCharsets.UTF_8);

        // El FROM de la segmentacion es la frontera de privacidad en su forma mas concreta. Si alguna
        // vez aparece ahi una tabla nueva, esta prueba lo saca a la luz en el diff.
        int fromStart = source.indexOf("AUDIENCE_FROM = \"\"\"");
        int fromEnd = source.indexOf("\"\"\";", fromStart);
        assertThat(fromStart).as("AUDIENCE_FROM debe existir y ser una constante").isPositive();

        String fromClause = source.substring(fromStart, fromEnd).toLowerCase(Locale.ROOT);
        assertThat(fromClause).contains("ref_patient", "patient_activity");
        assertThat(fromClause)
                .doesNotContain("diagnos", "encounter", "encuentro", "medical_record", "expediente",
                        "prescription", "allerg");
    }

    private static boolean mentionsForbiddenReference(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return FORBIDDEN_REFERENCES.stream().anyMatch(content::contains);
        } catch (IOException ignored) {
            // Un recurso binario no puede contener una referencia de configuracion.
            return false;
        }
    }
}
