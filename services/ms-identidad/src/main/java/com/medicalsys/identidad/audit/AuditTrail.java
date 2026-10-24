package com.medicalsys.identidad.audit;

import com.medicalsys.identidad.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Envio de eventos de seguridad de este servicio a la bitacora de ms-auditoria.
 *
 * <p>Dos decisiones gobiernan esta clase, las mismas que en el cliente equivalente de
 * ms-pacientes:
 *
 * <ul>
 *   <li><b>Un exito se publica despues del commit; una denegacion, de inmediato.</b> Un evento que
 *       anuncia un cambio de estado que despues se revierte contradice la base de datos, asi que un
 *       evento {@code SUCCESS} dentro de una transaccion se agenda en {@code afterCommit}. Un intento
 *       fallido, un bloqueo o un acceso denegado es un hecho aunque la operacion termine en error (de
 *       hecho, el login los produce justamente lanzando una excepcion que revierte la transaccion):
 *       esos se envian sin esperar el commit, o se perderian todos.</li>
 *   <li><b>El fallo de la bitacora no tumba la operacion de identidad.</b> Un login o un ABM de
 *       usuario no puede devolver 500 porque ms-auditoria esta caido. El envio ocurre fuera del
 *       hilo de la solicitud y un error se registra como advertencia; si la bitacora esta caida,
 *       se pierden eventos. La alternativa estricta -una bandeja de salida persistente- queda
 *       fuera de este alcance.</li>
 * </ul>
 *
 * <p>IP, metodo, ruta y user-agent se leen de la solicitud HTTP en curso a traves del
 * {@code RequestContextHolder} de Spring (disponible en el hilo del request incluso dentro de la
 * capa de servicio, sin necesidad de pasar el {@code HttpServletRequest} por cada firma).
 */
@Component
public class AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(AuditTrail.class);

    private static final String SERVICE_NAME = "ms-identidad";

    private final RestClient auditClient;
    private final ExecutorService auditExecutor;
    private final boolean enabled;

    public AuditTrail(@Qualifier("auditRestClient") RestClient auditClient,
            @Qualifier("auditExecutor") ExecutorService auditExecutor,
            AuditTrailProperties properties) {
        this.auditClient = auditClient;
        this.auditExecutor = auditExecutor;
        this.enabled = properties.isEnabled();
    }

    /**
     * Agenda el asiento del hecho descrito.
     *
     * <p>Nunca lanza: un problema de auditoria no puede convertirse en un error para quien esta
     * iniciando sesion o administrando una cuenta.
     */
    public void record(AuditEventDraft draft) {
        if (!enabled) {
            return;
        }
        try {
            Map<String, Object> body = toBody(draft, currentRequest(), currentCorrelationId());
            boolean deferUntilCommit = "SUCCESS".equals(draft.outcome())
                    && TransactionSynchronizationManager.isSynchronizationActive();
            if (deferUntilCommit) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        submit(body);
                    }
                });
            } else {
                submit(body);
            }
        } catch (RuntimeException exception) {
            log.warn("No se pudo preparar el evento de auditoria action={}", draft.action(), exception);
        }
    }

    private void submit(Map<String, Object> body) {
        auditExecutor.execute(() -> send(body));
    }

    private void send(Map<String, Object> body) {
        try {
            auditClient.post()
                    .uri("/internal/v1/audit-events")
                    .header(CorrelationIdFilter.HEADER, String.valueOf(body.get("correlationIdHint")))
                    .body(withoutHint(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("La bitacora rechazo o no recibio el evento action={} eventId={}",
                    body.get("action"), body.get("eventId"), exception);
        }
    }

    /**
     * Arma el cuerpo del evento con la identidad ya resuelta.
     *
     * <p>{@code eventId} se genera aqui y no en ms-auditoria para que un reintento del mismo
     * hecho sea idempotente.
     */
    private Map<String, Object> toBody(AuditEventDraft draft, HttpServletRequest request, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", UUID.randomUUID());
        body.put("serviceName", SERVICE_NAME);
        body.put("action", draft.action());
        body.put("category", draft.category());
        body.put("severity", draft.severity());
        body.put("outcome", draft.outcome());
        body.put("actorUserId", draft.actorUserId());
        body.put("actorDisplayName", draft.actorDisplayName());
        body.put("actorRoles", draft.actorRoles() == null ? List.of() : draft.actorRoles());
        body.put("actorIp", request == null ? null : request.getRemoteAddr());
        body.put("resourceType", draft.resourceType());
        body.put("resourcePublicId", draft.resourcePublicId());
        body.put("resourceLabel", draft.resourceLabel());
        body.put("requestMethod", request == null ? null : request.getMethod());
        body.put("requestPath", request == null ? null : request.getRequestURI());
        body.put("responseStatus", draft.responseStatus());
        body.put("reason", draft.reason());
        body.put("organizationId", draft.organizationId());
        body.put("metadata", withUserAgent(draft.metadata(), request));
        body.put("correlationIdHint", correlationId);
        return body;
    }

    /**
     * El contrato de ms-auditoria no tiene un campo dedicado a user-agent; viaja en metadata.
     * Nunca se agrega un dato sensible aqui, solo el encabezado estandar del navegador/cliente.
     */
    private Map<String, String> withUserAgent(Map<String, String> metadata, HttpServletRequest request) {
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        if (userAgent == null) {
            return metadata == null ? Map.of() : metadata;
        }
        Map<String, String> merged = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        merged.put("userAgent", userAgent);
        return merged;
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest()
                : null;
    }

    /** El correlationId viaja por header, como en el resto de saltos internos, no en el cuerpo. */
    private String currentCorrelationId() {
        String correlationId = MDC.get("correlationId");
        return correlationId == null ? "" : correlationId;
    }

    private Map<String, Object> withoutHint(Map<String, Object> body) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.remove("correlationIdHint");
        return copy;
    }
}
