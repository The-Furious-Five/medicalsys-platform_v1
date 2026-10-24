package com.medicalsys.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * LOG-02: deja en la bitacora de ms-auditoria cada 403 que un servicio devuelve a un usuario autenticado.
 *
 * <p>Los interceptores de permisos de los 13 servicios rechazan con 403, pero solo ms-identidad tenia
 * cliente de auditoria. Todo pasa por el gateway, que ademas conoce al actor (JWT) y su IP real, asi que
 * el registro se hace aqui una sola vez. ms-identidad se excluye porque ya audita sus propios 403.
 *
 * <p>El envio es asincrono y tolerante a fallos: una bitacora caida nunca frena ni rompe la solicitud.
 */
@Component
public class AccessDeniedAuditor {

    private static final Logger log = LoggerFactory.getLogger(AccessDeniedAuditor.class);
    private static final String IDENTITY_PREFIX = "/api/v1/identity/";

    private final RestClient client;
    private final ExecutorService executor;
    private final boolean enabled;

    public AccessDeniedAuditor(@Value("${AUDIT_SERVICE_URL:}") String auditServiceUrl) {
        this.enabled = StringUtils.hasText(auditServiceUrl);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder()
                .baseUrl(enabled ? auditServiceUrl : "http://localhost")
                .requestFactory(requestFactory)
                .build();
        // Cola acotada y descarte con aviso: bajo un ataque de fuerza bruta de 403 el gateway no se satura.
        this.executor = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
                runnable -> {
                    Thread thread = new Thread(runnable, "access-denied-audit");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Solo los 403 de servicios distintos de identidad, y solo con un usuario autenticado. */
    static boolean shouldRecord(Jwt jwt, int status, String path) {
        return jwt != null && status == 403 && path != null && !path.startsWith(IDENTITY_PREFIX);
    }

    public void record(Jwt jwt, HttpServletRequest request, String clientIp, String correlationId) {
        if (!enabled) {
            return;
        }
        Map<String, Object> body = body(jwt, request, clientIp, correlationId);
        try {
            executor.execute(() -> send(body, correlationId));
        } catch (RejectedExecutionException ex) {
            log.warn("Cola de auditoria llena: se descarta ACCESS_DENIED de {}", request.getRequestURI());
        }
    }

    static Map<String, Object> body(Jwt jwt, HttpServletRequest request, String clientIp, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", UUID.randomUUID());
        body.put("serviceName", "ms-gateway");
        body.put("action", "ACCESS_DENIED");
        body.put("category", "AUTHORIZATION");
        body.put("severity", "WARNING");
        body.put("outcome", "DENIED");
        body.put("actorUserId", parseUuid(jwt.getSubject()));
        body.put("actorDisplayName", jwt.getClaimAsString("email"));
        List<String> roles = jwt.getClaimAsStringList("roles");
        body.put("actorRoles", roles == null ? List.of() : roles);
        body.put("actorIp", clientIp);
        body.put("resourceType", "ENDPOINT");
        body.put("resourceLabel", request.getMethod() + " " + request.getRequestURI());
        body.put("requestMethod", request.getMethod());
        body.put("requestPath", request.getRequestURI());
        body.put("responseStatus", 403);
        body.put("reason", "El servicio rechazo la operacion por falta de permiso");
        body.put("organizationId", parseUuid(jwt.getClaimAsString("organizationId")));
        body.put("correlationIdHint", correlationId);
        return body;
    }

    private void send(Map<String, Object> body, String correlationId) {
        try {
            client.post()
                    .uri("/internal/v1/audit-events")
                    .header("X-Correlation-Id", correlationId)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar ACCESS_DENIED en la bitacora: {}", ex.getMessage());
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
