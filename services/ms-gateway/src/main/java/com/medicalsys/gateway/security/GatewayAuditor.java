package com.medicalsys.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * LOG-02: bitacora de toda accion de un usuario autenticado que pasa por el gateway.
 *
 * <p>Solo ms-identidad, ms-pacientes y ms-documental tenian cliente de auditoria, y solo para unos pocos
 * hechos: un cobro, una receta, una cita o un cambio de plan no dejaban rastro. Todo pasa por el gateway,
 * que conoce al actor (JWT), su IP real y el desenlace, asi que se registra aqui una sola vez:
 * <ul>
 *   <li>cada escritura (POST, PUT, PATCH, DELETE), con exito, error o denegacion;</li>
 *   <li>cada lectura de datos de paciente, historia clinica o documentos: quien vio que expediente;</li>
 *   <li>cada 403 ({@code ACCESS_DENIED}) y cada uso de un token cuya sesion ya se cerro.</li>
 * </ul>
 * ms-identidad se excluye porque audita sus propias acciones con mas detalle (login, ABM, roles, MAC).
 *
 * <p>El envio es asincrono y tolerante a fallos: una bitacora caida nunca frena ni rompe la solicitud.
 */
@Component
public class GatewayAuditor {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuditor.class);
    private static final String IDENTITY_PREFIX = "/api/v1/identity/";
    private static final Pattern UUID_IN_PATH = Pattern.compile(
            "/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?=/|$)");
    private static final Pattern PATIENT_PATH = Pattern.compile("^/api/v1/patients/([0-9a-fA-F-]{36})(?=/|$)");
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> SENSITIVE_READ_CATEGORIES = Set.of("PATIENT_DATA", "CLINICAL_RECORD", "DOCUMENT");

    /** Primer segmento de /api/v1/{segmento} → servicio y categoria, segun las rutas de application.yaml. */
    private static final Map<String, Route> ROUTES = Map.ofEntries(
            Map.entry("tenancy", new Route("ms-tenencia", "CONFIGURATION")),
            Map.entry("catalogs", new Route("ms-catalogos", "CONFIGURATION")),
            Map.entry("cryptography", new Route("ms-criptografia", "CONFIGURATION")),
            Map.entry("patients", new Route("ms-pacientes", "PATIENT_DATA")),
            Map.entry("professionals", new Route("ms-profesionales", "CONFIGURATION")),
            Map.entry("scheduling", new Route("ms-agenda", "SCHEDULING")),
            Map.entry("medical-records", new Route("ms-historial", "CLINICAL_RECORD")),
            Map.entry("encuentros", new Route("ms-historial", "CLINICAL_RECORD")),
            Map.entry("expedientes", new Route("ms-historial", "CLINICAL_RECORD")),
            Map.entry("ordenes-estudio", new Route("ms-historial", "CLINICAL_RECORD")),
            Map.entry("diagnosticos", new Route("ms-historial", "CLINICAL_RECORD")),
            Map.entry("alergias", new Route("ms-historial", "CLINICAL_RECORD")),
            Map.entry("antecedentes", new Route("ms-historial", "CLINICAL_RECORD")),
            Map.entry("recetas", new Route("ms-historial", "CLINICAL_RECORD")),
            Map.entry("documents", new Route("ms-documental", "DOCUMENT")),
            Map.entry("marketing", new Route("ms-marketing", "CONFIGURATION")),
            Map.entry("campanas", new Route("ms-marketing", "CONFIGURATION")),
            Map.entry("segmentos", new Route("ms-marketing", "CONFIGURATION")),
            Map.entry("promociones", new Route("ms-marketing", "CONFIGURATION")),
            Map.entry("anuncios", new Route("ms-marketing", "CONFIGURATION")),
            Map.entry("fidelidad", new Route("ms-marketing", "CONFIGURATION")),
            Map.entry("pacientes", new Route("ms-marketing", "CONFIGURATION")),
            Map.entry("audit", new Route("ms-auditoria", "SECURITY")),
            Map.entry("billing", new Route("ms-facturacion", "BILLING")),
            Map.entry("facturas", new Route("ms-facturacion", "BILLING")),
            Map.entry("notifications", new Route("ms-notificaciones", "CONFIGURATION")));

    private record Route(String serviceName, String category) {
    }

    private final RestClient client;
    private final ExecutorService executor;
    private final boolean enabled;

    public GatewayAuditor(@Value("${AUDIT_SERVICE_URL:}") String auditServiceUrl) {
        this.enabled = StringUtils.hasText(auditServiceUrl);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder()
                .baseUrl(enabled ? auditServiceUrl : "http://localhost")
                .requestFactory(requestFactory)
                .build();
        // Cola acotada y descarte con aviso: bajo un ataque (fuerza bruta de 403) el gateway no se satura.
        this.executor = new ThreadPoolExecutor(1, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2000),
                runnable -> {
                    Thread thread = new Thread(runnable, "gateway-audit");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Evento de la solicitud ya atendida, o null si no se asienta: sin usuario, de identidad, fuera de
     * /api/v1, un ping, o una lectura de algo que no es dato de paciente, clinico ni documental.
     */
    static Map<String, Object> describe(Jwt jwt, String method, String path, int status) {
        if (jwt == null || path == null || !path.startsWith("/api/v1/") || path.startsWith(IDENTITY_PREFIX)
                || path.endsWith("/ping")) {
            return null;
        }
        String verb = method.toUpperCase(Locale.ROOT);
        Route route = route(path);
        boolean write = WRITE_METHODS.contains(verb);
        if (status != 403 && !write && !("GET".equals(verb) && SENSITIVE_READ_CATEGORIES.contains(route.category()))) {
            return null;
        }

        Map<String, Object> body = base(jwt, route.serviceName());
        String outcome = status == 401 || status == 403 ? "DENIED" : status >= 400 ? "FAILURE" : "SUCCESS";
        if (status == 403) {
            body.put("action", "ACCESS_DENIED");
            body.put("category", "AUTHORIZATION");
            body.put("severity", "WARNING");
            body.put("reason", "El servicio rechazo la operacion por falta de permiso");
        } else {
            body.put("action", switch (verb) {
                case "POST" -> "API_CREATE";
                case "PUT", "PATCH" -> "API_UPDATE";
                case "DELETE" -> "API_DELETE";
                default -> "API_READ";
            });
            body.put("category", route.category());
            // Leer historia clinica es lo primero que revisa una auditoria clinica: se marca como relevante.
            boolean clinicalRead = !write && "CLINICAL_RECORD".equals(route.category());
            body.put("severity", "DELETE".equals(verb) ? "WARNING"
                    : !"SUCCESS".equals(outcome) || clinicalRead ? "NOTICE" : "INFO");
            if (!"SUCCESS".equals(outcome)) {
                body.put("reason", "La operacion termino con HTTP " + status);
            }
        }
        body.put("outcome", outcome);
        body.put("resourceType", resourceType(path));
        body.put("resourcePublicId", lastUuid(path));
        body.put("resourceLabel", verb + " " + path);
        body.put("patientPublicId", patientId(path));
        body.put("requestMethod", verb);
        body.put("requestPath", path);
        body.put("responseStatus", status);
        return body;
    }

    /** Un token valido cuya sesion ya se cerro (logout, baja, cambio de roles o de nivel MAC). */
    static Map<String, Object> describeRevokedSession(Jwt jwt, String method, String path) {
        Map<String, Object> body = base(jwt, "ms-gateway");
        body.put("action", "REVOKED_SESSION_USED");
        body.put("category", "AUTHENTICATION");
        body.put("severity", "WARNING");
        body.put("outcome", "DENIED");
        body.put("resourceType", "SESSION");
        body.put("resourceLabel", method + " " + path);
        body.put("requestMethod", method);
        body.put("requestPath", path);
        body.put("responseStatus", 401);
        body.put("reason", "Se uso un token de una sesion ya cerrada");
        return body;
    }

    public void record(Jwt jwt, HttpServletRequest request, int status, String clientIp, String correlationId) {
        submit(describe(jwt, request.getMethod(), request.getRequestURI(), status), request, clientIp, correlationId);
    }

    public void recordRevokedSession(Jwt jwt, HttpServletRequest request, String clientIp, String correlationId) {
        submit(describeRevokedSession(jwt, request.getMethod(), request.getRequestURI()), request, clientIp,
                correlationId);
    }

    private void submit(Map<String, Object> body, HttpServletRequest request, String clientIp, String correlationId) {
        if (!enabled || body == null || body.get("organizationId") == null) {
            return;
        }
        body.put("actorIp", clientIp);
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            body.put("metadata", Map.of("userAgent", userAgent.length() > 200 ? userAgent.substring(0, 200) : userAgent,
                    "observedBy", "ms-gateway"));
        } else {
            body.put("metadata", Map.of("observedBy", "ms-gateway"));
        }
        try {
            executor.execute(() -> send(body, correlationId));
        } catch (RejectedExecutionException ex) {
            log.warn("Cola de auditoria llena: se descarta {} de {}", body.get("action"), body.get("requestPath"));
        }
    }

    private static Map<String, Object> base(Jwt jwt, String serviceName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", UUID.randomUUID());
        body.put("organizationId", parseUuid(jwt.getClaimAsString("organizationId")));
        body.put("serviceName", serviceName);
        body.put("actorUserId", parseUuid(jwt.getSubject()));
        body.put("actorDisplayName", jwt.getClaimAsString("email"));
        List<String> roles = jwt.getClaimAsStringList("roles");
        body.put("actorRoles", roles == null ? List.of() : roles.stream().limit(8).toList());
        return body;
    }

    private static Route route(String path) {
        String segment = path.substring("/api/v1/".length()).split("/", 2)[0];
        return ROUTES.getOrDefault(segment, new Route("ms-gateway", "SYSTEM"));
    }

    /** ENCUENTROS, PATIENTS_ACCESO...: los segmentos que no son identificadores, hasta tres. */
    static String resourceType(String path) {
        StringBuilder type = new StringBuilder();
        int parts = 0;
        for (String segment : path.substring("/api/v1/".length()).split("/")) {
            if (segment.isBlank() || UUID_IN_PATH.matcher("/" + segment).matches() || segment.matches("\\d+")) {
                continue;
            }
            if (parts++ == 3) {
                break;
            }
            if (!type.isEmpty()) {
                type.append('_');
            }
            type.append(segment.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_"));
        }
        String value = type.length() > 60 ? type.substring(0, 60) : type.toString();
        return value.length() >= 3 && Character.isLetter(value.charAt(0)) ? value : "ENDPOINT";
    }

    private static UUID lastUuid(String path) {
        Matcher matcher = UUID_IN_PATH.matcher(path);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return parseUuid(last);
    }

    private static UUID patientId(String path) {
        Matcher matcher = PATIENT_PATH.matcher(path);
        return matcher.find() ? parseUuid(matcher.group(1)) : null;
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
            log.warn("No se pudo registrar {} en la bitacora: {}", body.get("action"), ex.getMessage());
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
