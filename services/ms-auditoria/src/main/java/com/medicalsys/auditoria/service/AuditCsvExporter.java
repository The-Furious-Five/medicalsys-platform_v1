package com.medicalsys.auditoria.service;

import com.medicalsys.auditoria.domain.model.AuditEvent;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Serializacion CSV del tramo consultado.
 *
 * <p>El auditor necesita llevarse la evidencia a una planilla o a un expediente. Se escribe
 * directamente sobre el {@link Writer} de la respuesta en vez de armar la cadena completa en
 * memoria: una exportacion legitima puede cubrir decenas de miles de filas.
 */
@Component
public class AuditCsvExporter {

    private static final List<String> HEADERS = List.of(
            "eventId", "sequenceNumber", "occurredAt", "recordedAt", "service", "action", "category",
            "severity", "outcome", "actorUserId", "actorDisplayName", "actorRoles", "actorIp",
            "resourceType", "resourcePublicId", "resourceLabel", "patientPublicId", "correlationId",
            "requestMethod", "requestPath", "responseStatus", "reason", "metadata", "recordHash");

    public void writeHeader(Writer writer) throws IOException {
        writer.write(String.join(",", HEADERS));
        writer.write("\n");
    }

    public void writeRow(Writer writer, AuditEvent event) throws IOException {
        writer.write(String.join(",", List.of(
                cell(event.getPublicId()),
                cell(event.getSequenceNumber()),
                cell(event.getOccurredAt()),
                cell(event.getRecordedAt()),
                cell(event.getServiceName()),
                cell(event.getAction()),
                cell(event.getCategory()),
                cell(event.getSeverity()),
                cell(event.getOutcome()),
                cell(event.getActorUserId()),
                cell(event.getActorDisplayName()),
                cell(event.getActorRoles()),
                cell(event.getActorIp()),
                cell(event.getResourceType()),
                cell(event.getResourcePublicId()),
                cell(event.getResourceLabel()),
                cell(event.getPatientPublicId()),
                cell(event.getCorrelationId()),
                cell(event.getRequestMethod()),
                cell(event.getRequestPath()),
                cell(event.getResponseStatus()),
                cell(event.getReason()),
                cell(event.getMetadataJson()),
                cell(event.getRecordHash()))));
        writer.write("\n");
    }

    /**
     * Escapa un valor segun RFC 4180 y neutraliza la inyeccion de formulas.
     *
     * <p>Un valor que empieza por {@code =}, {@code +}, {@code -} o {@code @} lo ejecuta la hoja de
     * calculo al abrir el archivo. Como parte del contenido proviene de rutas y etiquetas que
     * alguien externo pudo influir, se antepone un apostrofo para que la celda quede como texto.
     */
    private String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = Objects.toString(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
