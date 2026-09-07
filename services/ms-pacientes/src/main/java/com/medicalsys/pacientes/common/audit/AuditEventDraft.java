package com.medicalsys.pacientes.common.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Hecho que ms-pacientes quiere dejar asentado en ms-auditoria.
 *
 * <p>Es el cuerpo de {@code POST /internal/v1/audit-events} y por eso repite sus nombres de campo.
 * El actor, la organizacion y el correlationId no estan aqui: los completa {@link AuditTrail} desde
 * el contexto de la solicitud, para que ningun caso de uso pueda declarar un autor distinto del que
 * realmente ejecuto la operacion.
 *
 * @param action       codigo estable del hecho, p. ej. {@code PATIENT_REGISTERED}
 * @param category     area funcional; para este servicio casi siempre {@code PATIENT_DATA}
 * @param severity     relevancia para un auditor
 * @param outcome      desenlace de la operacion
 * @param resourceType tipo de recurso afectado
 * @param metadata     contexto adicional; nunca dato personal ni clinico
 */
public record AuditEventDraft(
        String action,
        String category,
        String severity,
        String outcome,
        String resourceType,
        UUID resourcePublicId,
        String resourceLabel,
        UUID patientPublicId,
        Integer responseStatus,
        String reason,
        Map<String, String> metadata) {

    /** Alta o modificacion exitosa del registro administrativo de un paciente. */
    public static AuditEventDraft patientWrite(String action, UUID patientId, int responseStatus,
            Map<String, String> metadata) {
        return new AuditEventDraft(action, "PATIENT_DATA", "NOTICE", "SUCCESS", "PATIENT", patientId,
                null, patientId, responseStatus, null, metadata);
    }
}
