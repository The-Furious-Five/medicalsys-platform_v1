package com.medicalsys.auditoria.service;

import com.medicalsys.auditoria.domain.model.AuditEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Calculo del encadenamiento a prueba de manipulacion.
 *
 * <p>Cada asiento se resume con {@code SHA-256} sobre una representacion canonica de su contenido
 * concatenada con el hash del asiento anterior de la misma organizacion. Modificar un evento ya
 * escrito, o quitarlo, deja de reproducir el hash de todos los que vinieron despues, asi que la
 * manipulacion se vuelve detectable sin necesidad de una copia externa de la bitacora.
 *
 * <p>La representacion es canonica en un sentido estricto: orden fijo de campos, separador que no
 * puede aparecer en el contenido ({@code US}, 0x1F), nulos distinguibles de la cadena vacia y
 * fechas en epoch de microsegundos. Si dos implementaciones no coinciden byte a byte, la
 * verificacion no vale nada.
 */
@Component
public class AuditHashChain {

    /** Unit Separator: no aparece en ningun campo aceptado por el contrato. */
    private static final char FIELD_SEPARATOR = (char) 0x1F;

    private static final String NULL_MARKER = String.valueOf((char) 0x00);

    /**
     * Calcula el hash de un asiento a partir del hash del anterior.
     *
     * @param previousHash hash del evento previo de la organizacion, o null si es el primero
     */
    public String hash(String previousHash, UUID publicId, UUID organizationId, long sequenceNumber,
            Instant occurredAt, Instant recordedAt, String serviceName, String action, String category,
            String severity, String outcome, UUID actorUserId, String actorDisplayName, String actorRoles,
            String actorIp, String resourceType, UUID resourcePublicId, String resourceLabel,
            UUID patientPublicId, String correlationId, String requestMethod, String requestPath,
            Integer responseStatus, String reason, String metadataJson) {
        StringJoiner canonical = new StringJoiner(String.valueOf(FIELD_SEPARATOR));
        canonical.add(text(previousHash));
        canonical.add(text(publicId));
        canonical.add(text(organizationId));
        canonical.add(Long.toString(sequenceNumber));
        canonical.add(epochMicros(occurredAt));
        canonical.add(epochMicros(recordedAt));
        canonical.add(text(serviceName));
        canonical.add(text(action));
        canonical.add(text(category));
        canonical.add(text(severity));
        canonical.add(text(outcome));
        canonical.add(text(actorUserId));
        canonical.add(text(actorDisplayName));
        canonical.add(text(actorRoles));
        canonical.add(text(actorIp));
        canonical.add(text(resourceType));
        canonical.add(text(resourcePublicId));
        canonical.add(text(resourceLabel));
        canonical.add(text(patientPublicId));
        canonical.add(text(correlationId));
        canonical.add(text(requestMethod));
        canonical.add(text(requestPath));
        canonical.add(responseStatus == null ? NULL_MARKER : Integer.toString(responseStatus));
        canonical.add(text(reason));
        canonical.add(text(metadataJson));
        return sha256(canonical.toString());
    }

    /** Recalcula el hash de un evento ya persistido, para la verificacion de integridad. */
    public String hashOf(AuditEvent event) {
        return hash(event.getPreviousHash(), event.getPublicId(), event.getOrganizationId(),
                event.getSequenceNumber(), event.getOccurredAt(), event.getRecordedAt(),
                event.getServiceName(), event.getAction(), event.getCategory().name(),
                event.getSeverity().name(), event.getOutcome().name(), event.getActorUserId(),
                event.getActorDisplayName(), event.getActorRoles(), event.getActorIp(),
                event.getResourceType(), event.getResourcePublicId(), event.getResourceLabel(),
                event.getPatientPublicId(), event.getCorrelationId(), event.getRequestMethod(),
                event.getRequestPath(), event.getResponseStatus(), event.getReason(),
                event.getMetadataJson());
    }

    private String text(Object value) {
        return value == null ? NULL_MARKER : value.toString();
    }

    /**
     * Fecha en microsegundos desde epoch.
     *
     * <p>Coincide con la precision de {@code DATETIME(6)} de la tabla. Usar la representacion ISO
     * dejaria el hash a merced del formateador y de la zona en que se serialice.
     */
    private String epochMicros(Instant value) {
        if (value == null) {
            return NULL_MARKER;
        }
        return Long.toString(value.getEpochSecond() * 1_000_000L + value.getNano() / 1_000L);
    }

    private String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 es obligatorio en toda JVM; si falta, el entorno no es apto para auditar.
            throw new IllegalStateException("La JVM no ofrece SHA-256", ex);
        }
    }
}
