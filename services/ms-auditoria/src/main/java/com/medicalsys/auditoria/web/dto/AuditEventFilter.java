package com.medicalsys.auditoria.web.dto;

import com.medicalsys.auditoria.domain.enums.AuditCategory;
import com.medicalsys.auditoria.domain.enums.AuditOutcome;
import com.medicalsys.auditoria.domain.enums.AuditSeverity;
import java.time.Instant;
import java.util.UUID;

/**
 * Criterios de busqueda de la pantalla de auditoria.
 *
 * <p>Todos son opcionales y se combinan con Y logico. El tenant no esta aqui: sale del contexto de
 * la solicitud y no se puede pedir por parametro, que es justamente lo que impide que un
 * administrador de una clinica consulte la bitacora de otra.
 *
 * @param from          limite inferior inclusivo de {@code occurredAt}
 * @param to            limite superior exclusivo de {@code occurredAt}
 * @param actorUserId   usuario que ejecuto la accion
 * @param patientPublicId paciente afectado; responde "quien accedio a este paciente"
 * @param resourcePublicId recurso concreto afectado
 * @param q             texto libre sobre accion, recurso, actor y motivo
 */
public record AuditEventFilter(
        Instant from,
        Instant to,
        AuditCategory category,
        AuditOutcome outcome,
        AuditSeverity severity,
        String serviceName,
        String action,
        String resourceType,
        UUID actorUserId,
        UUID patientPublicId,
        UUID resourcePublicId,
        String correlationId,
        String q) {
}
