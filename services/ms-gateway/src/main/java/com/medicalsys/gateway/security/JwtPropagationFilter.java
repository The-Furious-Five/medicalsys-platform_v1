package com.medicalsys.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida el JWT emitido por ms-identidad y repropaga la identidad como headers de confianza
 * hacia los servicios internos, tal como esperan sus TenantContextFilter (ms-tenencia,
 * ms-historial, ms-facturacion, ms-pacientes, ms-documental): X-User-Id, X-Organization-Id,
 * X-Roles, y X-Permissions con los permisos del claim permissions (RBAC-03), que cada servicio exige
 * con @RequiresPermission. Tambien garantiza X-Correlation-Id en cada salto, con la misma convencion de header
 * ya usada por esos mismos servicios (CorrelationIdFilter).
 *
 * <p>Alcance deliberadamente acotado (no es una matriz de autorizacion por ruta):
 * <ul>
 *   <li>Si no llega Authorization, el filtro no rechaza la solicitud: varias rutas reales son
 *       publicas por diseno (login, /ping, catalogos, /internal/v1/crypto, y hoy tambien
 *       ms-agenda/ms-profesionales, que no exigen JWT en su propio codigo). Se limita a retirar
 *       cualquier X-User-Id/X-Organization-Id/X-Roles/X-Permissions que el cliente haya enviado el mismo, para
 *       que nadie pueda suplantar un tenant sin pasar por ms-identidad. El servicio de destino
 *       sigue decidiendo si esa ausencia de contexto es un 401 (p. ej. TenantContextFilter).</li>
 *   <li>Si llega Authorization pero el token es invalido o expiro, aqui si se corta con 401: un
 *       JWT invalido nunca es una entrada legitima para ningun servicio de destino.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtPropagationFilter extends OncePerRequestFilter {

    static final String ORGANIZATION_HEADER = "X-Organization-Id";
    static final String USER_HEADER = "X-User-Id";
    static final String ROLES_HEADER = "X-Roles";
    static final String PERMISSIONS_HEADER = "X-Permissions";
    static final String CORRELATION_HEADER = "X-Correlation-Id";
    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    static final String CLIENT_IP_HEADER = "X-Client-Ip";

    private final JwtDecoder jwtDecoder;
    private final StringRedisTemplate redis;
    private final GatewayAuditor auditor;

    public JwtPropagationFilter(JwtDecoder jwtDecoder, StringRedisTemplate redis, GatewayAuditor auditor) {
        this.jwtDecoder = jwtDecoder;
        this.redis = redis;
        this.auditor = auditor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Antes de cualquier corte: los 401 y 503 de este filtro tambien deben llevar las cabeceras.
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", "frame-ancestors 'none'");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("Server", "");

        Map<String, String> overrides = new LinkedHashMap<>();

        String correlationId = request.getHeader(CORRELATION_HEADER);
        overrides.put(CORRELATION_HEADER, (correlationId == null || correlationId.isBlank())
                ? UUID.randomUUID().toString()
                : correlationId.trim());
        // LOG-01: la bitacora necesita la IP del usuario, no la del ultimo salto. Va en una cabecera propia
        // que solo pone el gateway (la del cliente se descarta, como X-User-Id): X-Forwarded-For lo reescribe
        // el propio proxy y un cliente tambien podria mandarlo.
        overrides.put(CLIENT_IP_HEADER, clientIp(request));

        Jwt jwt = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                jwt = jwtDecoder.decode(token);
            } catch (JwtException ex) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"title\":\"Token invalido\",\"detail\":\"El token de acceso es invalido o expiro\"}");
                return;
            }

            try {
                if (jwt.getId() == null || !Boolean.TRUE.equals(redis.hasKey("identity:session:" + jwt.getId()))) {
                    auditor.recordRevokedSession(jwt, request, overrides.get(CLIENT_IP_HEADER),
                            overrides.get(CORRELATION_HEADER));
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"title\":\"Sesion revocada\",\"detail\":\"La sesion ya no esta activa\"}");
                    return;
                }
            } catch (DataAccessException ex) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json");
                response.getWriter().write("{\"title\":\"Servicio no disponible\",\"detail\":\"No se pudo verificar la sesion\"}");
                return;
            }

            overrides.put(USER_HEADER, jwt.getSubject());
            String organizationId = jwt.getClaimAsString("organizationId");
            if (organizationId != null && !organizationId.isBlank()) {
                overrides.put(ORGANIZATION_HEADER, organizationId);
            }
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null && !roles.isEmpty()) {
                overrides.put(ROLES_HEADER, String.join(",", roles));
            }
            List<String> permissions = jwt.getClaimAsStringList("permissions");
            if (permissions != null && !permissions.isEmpty()) {
                overrides.put(PERMISSIONS_HEADER, String.join(",", permissions));
            }
        }

        // LOG-03: el correlation id acompana cada linea de log de esta solicitud.
        MDC.put("correlationId", overrides.get(CORRELATION_HEADER));
        try {
            chain.doFilter(new TenantHeaderRequestWrapper(request, overrides), response);
        } finally {
            MDC.remove("correlationId");
        }

        // LOG-02: cada accion del usuario queda en la bitacora; GatewayAuditor decide cuales se asientan.
        auditor.record(jwt, request, response.getStatus(), overrides.get(CLIENT_IP_HEADER),
                overrides.get(CORRELATION_HEADER));
    }

    /**
     * IP del usuario. X-Forwarded-For solo se cree si la conexion llega de un proxy de la red interna
     * (nginx del frontend o Caddy, que reescribe la cabecera con la IP real): un cliente que entra
     * directo desde internet no puede inventarse una IP mandando la cabecera el mismo.
     */
    static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwarded != null && !forwarded.isBlank() && isInternal(remote)) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return remote;
    }

    private static boolean isInternal(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try {
            InetAddress inet = InetAddress.getByName(address);
            return inet.isLoopbackAddress() || inet.isSiteLocalAddress() || inet.isLinkLocalAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    /**
     * Envuelve la solicitud para que los headers de tenant reflejen solo lo derivado del JWT
     * validado arriba: nunca lo que haya mandado el cliente directamente.
     */
    private static final class TenantHeaderRequestWrapper extends HttpServletRequestWrapper {

        private static final Set<String> TRUSTED_HEADERS = Set.of(
                ORGANIZATION_HEADER.toLowerCase(Locale.ROOT),
                USER_HEADER.toLowerCase(Locale.ROOT),
                ROLES_HEADER.toLowerCase(Locale.ROOT),
                PERMISSIONS_HEADER.toLowerCase(Locale.ROOT),
                CORRELATION_HEADER.toLowerCase(Locale.ROOT),
                CLIENT_IP_HEADER.toLowerCase(Locale.ROOT));

        private final Map<String, String> overridesByLowerName = new LinkedHashMap<>();

        TenantHeaderRequestWrapper(HttpServletRequest request, Map<String, String> overrides) {
            super(request);
            overrides.forEach((name, value) -> overridesByLowerName.put(name.toLowerCase(Locale.ROOT), value));
        }

        @Override
        public String getHeader(String name) {
            String key = name.toLowerCase(Locale.ROOT);
            if (overridesByLowerName.containsKey(key)) {
                return overridesByLowerName.get(key);
            }
            return TRUSTED_HEADERS.contains(key) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String key = name.toLowerCase(Locale.ROOT);
            if (overridesByLowerName.containsKey(key)) {
                return Collections.enumeration(List.of(overridesByLowerName.get(key)));
            }
            return TRUSTED_HEADERS.contains(key) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                if (!TRUSTED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    names.add(name);
                }
            }
            names.addAll(overridesByLowerName.keySet());
            return Collections.enumeration(names);
        }
    }
}
