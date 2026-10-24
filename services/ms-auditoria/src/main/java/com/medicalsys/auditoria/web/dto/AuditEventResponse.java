package com.medicalsys.auditoria.web.dto;

import com.medicalsys.auditoria.domain.enums.AuditCategory;
import com.medicalsys.auditoria.domain.enums.AuditOutcome;
import com.medicalsys.auditoria.domain.enums.AuditSeverity;
import com.medicalsys.auditoria.domain.model.AuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento tal como lo lee la pantalla de auditoria.
 *
 * <p>No expone {@code id} ni ningun detalle fisico. Si expone {@code sequenceNumber},
 * {@code previousHash} y {@code recordHash}: son lo que permite a un revisor externo recalcular la
 * cadena por su cuenta en vez de confiar en la palabra del servicio.
 */
public record AuditEventResponse(
        UUID eventId,
        UUID organizationId,
        long sequenceNumber,
        Instant occurredAt,
        Instant recordedAt,
        String serviceName,
        String action,
        AuditCategory category,
        AuditSeverity severity,
        AuditOutcome outcome,
        UUID actorUserId,
        String actorDisplayName,
        List<String> actorRoles,
        String actorIp,
        String resourceType,
        UUID resourcePublicId,
        String resourceLabel,
        UUID patientPublicId,
        String correlationId,
        String requestMethod,
        String requestPath,
        Integer responseStatus,
        String reason,
        String metadata,
        String previousHash,
        String recordHash) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getPublicId(),
                event.getOrganizationId(),
                event.getSequenceNumber(),
                event.getOccurredAt(),
                event.getRecordedAt(),
                event.getServiceName(),
                event.getAction(),
                event.getCategory(),
                event.getSeverity(),
                event.getOutcome(),
                event.getActorUserId(),
                event.getActorDisplayName(),
                splitRoles(event.getActorRoles()),
                event.getActorIp(),
                event.getResourceType(),
                event.getResourcePublicId(),
                event.getResourceLabel(),
                event.getPatientPublicId(),
                event.getCorrelationId(),
                event.getRequestMethod(),
                event.getRequestPath(),
                event.getResponseStatus(),
                event.getReason(),
                event.getMetadataJson(),
                event.getPreviousHash(),
                event.getRecordHash());
    }

    private static List<String> splitRoles(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return List.of(stored.split(","));
    }
}
