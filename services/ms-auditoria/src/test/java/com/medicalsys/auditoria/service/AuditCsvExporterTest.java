package com.medicalsys.auditoria.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.medicalsys.auditoria.domain.enums.AuditCategory;
import com.medicalsys.auditoria.domain.enums.AuditOutcome;
import com.medicalsys.auditoria.domain.enums.AuditSeverity;
import com.medicalsys.auditoria.domain.model.AuditEvent;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditCsvExporterTest {

    private final AuditCsvExporter exporter = new AuditCsvExporter();

    /** Una comilla dentro de un campo no puede partir la fila en dos columnas. */
    @Test
    void escapesQuotesInsideAField() throws IOException {
        StringWriter writer = new StringWriter();

        exporter.writeRow(writer, event("Paciente \"demo\"", null));

        assertTrue(writer.toString().contains("\"Paciente \"\"demo\"\"\""), writer.toString());
    }

    /**
     * Un campo que empieza por {@code =} no debe ejecutarse al abrir el CSV.
     *
     * <p>Parte del contenido de la bitacora son rutas y etiquetas que un tercero pudo influir; si la
     * planilla las evalua, la exportacion de auditoria se convierte en un vector de ataque contra la
     * persona que la revisa.
     */
    @Test
    void neutralizesFormulasAtTheStartOfAField() throws IOException {
        StringWriter writer = new StringWriter();

        exporter.writeRow(writer, event("=1+1", null));

        assertTrue(writer.toString().contains("\"'=1+1\""), writer.toString());
    }

    /** Un campo nulo se escribe vacio y sin comillas, para no confundirlo con la cadena "null". */
    @Test
    void writesNullFieldsAsEmptyCells() throws IOException {
        StringWriter writer = new StringWriter();

        exporter.writeRow(writer, event("Paciente demo", null));

        assertTrue(writer.toString().contains(",,"), writer.toString());
    }

    private AuditEvent event(String resourceLabel, String reason) {
        return new AuditEvent(UUID.randomUUID(), UUID.randomUUID(), 1, Instant.parse("2026-09-07T12:00:00Z"),
                Instant.parse("2026-09-07T12:00:01Z"), "ms-pacientes", "PATIENT_REGISTERED",
                AuditCategory.PATIENT_DATA, AuditSeverity.NOTICE, AuditOutcome.SUCCESS, null, null, null,
                null, "PATIENT", null, resourceLabel, null, "corr-1", "POST", "/api/v1/patients", 201,
                reason, null, null, "a".repeat(64));
    }
}
