package com.medicalsys.identidad.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hecho que ms-identidad quiere dejar asentado en ms-auditoria.
 *
 * <p>Es el cuerpo de {@code POST /internal/v1/audit-events} y por eso repite sus nombres de
 * campo. El actor casi siempre es conocido de antemano aqui (el usuario que intenta loguearse, el
 * administrador que hace un ABM): a diferencia de un servicio que lee el actor del contexto de la
 * solicitud, en autenticacion el "actor" de un intento fallido puede ser justamente la cuenta cuyo
 * acceso se esta evaluando, asi que cada caso de uso lo declara explicitamente.
 *
 * <p>{@code metadata} nunca lleva contrasenas, tokens ni secretos: solo contexto de negocio.
 *
 * @param action           codigo estable del hecho, p. ej. {@code LOGIN_SUCCESS}
 * @param category         area funcional, segun el catalogo de ms-auditoria
 * @param severity         relevancia para un auditor
 * @param outcome          desenlace de la operacion
 * @param actorUserId      usuario que ejecuta la accion; nulo si no se pudo identificar (p. ej. correo inexistente)
 * @param actorDisplayName correo o nombre del actor, para lectura humana sin resolver el id
 * @param actorRoles       roles vigentes del actor al momento del hecho
 * @param resourceType     tipo de recurso afectado, p. ej. {@code USER_ACCOUNT} o {@code ROLE}
 * @param resourcePublicId recurso afectado (el objetivo del ABM, o la propia cuenta en autenticacion)
 * @param resourceLabel    descripcion legible del recurso afectado
 * @param organizationId   tenant del hecho, cuando se conoce
 * @param responseStatus   codigo HTTP con el que termino la operacion
 * @param reason            motivo declarado de un rechazo o de un cambio de estado
 * @param metadata          contexto adicional; nunca contrasenas ni tokens
 */
public record AuditEventDraft(
        String action,
        String category,
        String severity,
        String outcome,
        UUID actorUserId,
        String actorDisplayName,
        List<String> actorRoles,
        String resourceType,
        UUID resourcePublicId,
        String resourceLabel,
        UUID organizationId,
        Integer responseStatus,
        String reason,
        Map<String, String> metadata) {

    /** Intento de inicio o cierre de sesion. El actor es la propia cuenta evaluada. */
    public static AuditEventDraft authentication(String action, String outcome, UUID accountPublicId,
            String accountEmail, UUID organizationId, Integer responseStatus, String reason) {
        String severity = "SUCCESS".equals(outcome) ? "NOTICE" : "WARNING";
        return new AuditEventDraft(action, "AUTHENTICATION", severity, outcome, accountPublicId, accountEmail,
                List.of(), "USER_ACCOUNT", accountPublicId, accountEmail, organizationId, responseStatus, reason,
                Map.of());
    }

    /** ABM de cuentas, contrasenas o roles ejecutado por un administrador sobre un usuario objetivo. */
    public static AuditEventDraft userManagement(String action, UUID actorUserId, UUID targetPublicId,
            String targetEmail, UUID organizationId, String reason, Map<String, String> metadata) {
        return new AuditEventDraft(action, "CONFIGURATION", "NOTICE", "SUCCESS", actorUserId, null, List.of(),
                "USER_ACCOUNT", targetPublicId, targetEmail, organizationId, null, reason, metadata);
    }

    /** Denegacion de acceso detectada por el filtro de autorizacion. */
    public static AuditEventDraft accessDenied(UUID actorUserId, List<String> actorRoles, String requestPath,
            String reason) {
        return new AuditEventDraft("ACCESS_DENIED", "AUTHORIZATION", "WARNING", "DENIED", actorUserId, null,
                actorRoles, "ENDPOINT", null, requestPath, null, 403, reason, Map.of());
    }
}
