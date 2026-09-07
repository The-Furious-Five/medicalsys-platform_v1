package com.medicalsys.pacientes.common.audit;

import com.medicalsys.pacientes.common.correlation.CorrelationId;
import com.medicalsys.pacientes.common.tenant.RequestContext;
import com.medicalsys.pacientes.common.tenant.RequestContextHolder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Envio de eventos de este servicio a la bitacora de ms-auditoria.
 *
 * <p>Dos decisiones gobiernan esta clase:
 *
 * <ul>
 *   <li><b>Se publica despues del commit.</b> Un evento que anuncia un alta que despues se revierte
 *       es peor que no tener evento: contradice la base de datos. Cuando hay transaccion abierta, el
 *       envio se agenda en {@code afterCommit} y no se ejecuta si la operacion falla.</li>
 *   <li><b>El fallo de la bitacora no tumba la operacion clinica.</b> El envio ocurre fuera del hilo
 *       de la solicitud y un error se registra como advertencia. Es una decision de MVP y tiene un
 *       costo explicito: si ms-auditoria esta caido, se pierden eventos. La alternativa estricta
 *       -rechazar la escritura de paciente cuando no se puede auditar- requiere una bandeja de
 *       salida persistente, que queda fuera de este alcance.</li>
 * </ul>
 *
 * <p>El actor y la organizacion se leen del contexto de la solicitud <em>antes</em> de saltar de
 * hilo: el {@code ThreadLocal} ya no existe cuando el envio corre.
 */
@Component
public class AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(AuditTrail.class);

    private static final String SERVICE_NAME = "ms-pacientes";

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
     * <p>Nunca lanza: un problema de auditoria no puede convertirse en un 500 para el usuario que
     * estaba registrando a un paciente.
     */
    public void record(AuditEventDraft draft) {
        if (!enabled) {
            return;
        }
        try {
            Map<String, Object> body = toBody(draft, RequestContextHolder.currentOrNull(),
                    CorrelationId.current());
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
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
                    .header(CorrelationId.HEADER, String.valueOf(body.get("correlationIdHint")))
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
     * <p>{@code eventId} se genera aqui y no en ms-auditoria para que un reintento del mismo hecho
     * sea idempotente: la bitacora reconoce el identificador y devuelve el acuse ya emitido en vez
     * de duplicar la entrada.
     */
    private Map<String, Object> toBody(AuditEventDraft draft, RequestContext context, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", UUID.randomUUID());
        body.put("serviceName", SERVICE_NAME);
        body.put("action", draft.action());
        body.put("category", draft.category());
        body.put("severity", draft.severity());
        body.put("outcome", draft.outcome());
        body.put("resourceType", draft.resourceType());
        body.put("resourcePublicId", draft.resourcePublicId());
        body.put("resourceLabel", draft.resourceLabel());
        body.put("patientPublicId", draft.patientPublicId());
        body.put("responseStatus", draft.responseStatus());
        body.put("reason", draft.reason());
        body.put("metadata", draft.metadata());
        if (context != null) {
            body.put("organizationId", context.organizationId());
            body.put("actorUserId", context.userId());
            body.put("actorRoles", context.roles().stream().map(Enum::name).toList());
        } else {
            body.put("actorRoles", List.of());
        }
        body.put("correlationIdHint", correlationId);
        return body;
    }

    /** El correlationId viaja por header, como en el resto de saltos internos, no en el cuerpo. */
    private Map<String, Object> withoutHint(Map<String, Object> body) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.remove("correlationIdHint");
        return copy;
    }
}
