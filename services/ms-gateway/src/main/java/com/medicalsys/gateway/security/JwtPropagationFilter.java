package com.medicalsys.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
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

    private final JwtDecoder jwtDecoder;

    public JwtPropagationFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Map<String, String> overrides = new LinkedHashMap<>();

        String correlationId = request.getHeader(CORRELATION_HEADER);
        overrides.put(CORRELATION_HEADER, (correlationId == null || correlationId.isBlank())
                ? UUID.randomUUID().toString()
                : correlationId.trim());

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            Jwt jwt;
            try {
                jwt = jwtDecoder.decode(token);
            } catch (JwtException ex) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"title\":\"Token invalido\",\"detail\":\"El token de acceso es invalido o expiro\"}");
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

        chain.doFilter(new TenantHeaderRequestWrapper(request, overrides), response);
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
                CORRELATION_HEADER.toLowerCase(Locale.ROOT));

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
