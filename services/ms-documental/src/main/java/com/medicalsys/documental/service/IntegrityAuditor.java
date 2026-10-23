package com.medicalsys.documental.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Registra en ms-auditoria (POST /internal/v1/audit-events) las discrepancias de integridad.
 * Si AUDIT_SERVICE_URL esta vacio no envia nada; un fallo de la bitacora nunca rompe la respuesta.
 */
@Component
public class IntegrityAuditor {

    private static final Logger log = LoggerFactory.getLogger(IntegrityAuditor.class);

    private final RestClient client;

    public IntegrityAuditor(@Value("${medicalsys.audit.base-url:}") String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            this.client = null;
            return;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    // ponytail: envio sincrono con timeout de 2s/5s y sin reintento; mover a cola async/outbox si
    // la descarga no debe esperar a la bitacora.
    public void integrityMismatch(UUID documentId, UUID organizationId, String expected, String actual) {
        if (client == null) {
            return;
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("eventId", UUID.randomUUID());
        body.put("serviceName", "ms-documental");
        body.put("action", "DOCUMENT_INTEGRITY_MISMATCH");
        body.put("category", "DOCUMENT");
        body.put("severity", "CRITICAL");
        body.put("outcome", "FAILURE");
        body.put("actorRoles", List.of());
        body.put("resourceType", "DOCUMENT");
        body.put("resourcePublicId", documentId);
        body.put("organizationId", organizationId);
        body.put("responseStatus", 409);
        body.put("reason", "El SHA-256 recalculado no coincide con el registrado al subir el archivo");
        body.put("metadata", Map.of("expectedSha256", expected, "actualSha256", actual));
        try {
            client.post().uri("/internal/v1/audit-events").body(body).retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            log.warn("No se pudo enviar el evento de auditoria DOCUMENT_INTEGRITY_MISMATCH documentId={}",
                    documentId, exception);
        }
    }
}
