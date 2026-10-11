package com.medicalsys.catalogos.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Publica la identidad propagada por el gateway en {@link RequestContextHolder}.
 *
 * <p>El gateway retira cualquier {@code X-User-Id}/{@code X-Organization-Id}/{@code X-Roles}
 * que el cliente haya enviado él mismo y solo repropaga estos headers cuando decodificó un JWT
 * válido (ver {@code JwtPropagationFilter} de ms-gateway). Este filtro nunca confía en un
 * organizationId o userId que no venga por ese camino: si faltan, la solicitud sencillamente
 * queda sin contexto y {@link RequestContextHolder#require()} la rechazará con 401 donde el
 * catálogo lo exija (RF-11.1).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String USER_HEADER = "X-User-Id";
    public static final String ORGANIZATION_HEADER = "X-Organization-Id";
    public static final String ROLES_HEADER = "X-Roles";
    public static final String PERMISSIONS_HEADER = "X-Permissions";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RequestContext context = readContext(request);
        if (context != null) {
            RequestContextHolder.set(context);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            RequestContextHolder.clear();
        }
    }

    /** Construye el contexto a partir de los headers de confianza; null si no hay identidad. */
    private RequestContext readContext(HttpServletRequest request) {
        UUID userId = parseUuid(request.getHeader(USER_HEADER));
        if (userId == null) {
            return null;
        }
        return new RequestContext(userId, parseUuid(request.getHeader(ORGANIZATION_HEADER)),
                parseRoles(request.getHeader(ROLES_HEADER)), parsePermissions(request.getHeader(PERMISSIONS_HEADER)));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Set<String> parseRoles(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> parsePermissions(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(permission -> !permission.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }
}
