package com.medicalsys.profesionales.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Publica la identidad propagada por el gateway en {@link RequestContextHolder}.
 *
 * <p>El gateway elimina cualquier header enviado por el cliente y lo recrea desde claims firmados;
 * este filtro nunca acepta un organizationId como parametro de consulta ni de cuerpo. Sin esto,
 * cualquiera podia pedir /api/v1/professionals?organizationId=... con el id de otra clinica.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String ORGANIZATION_HEADER = "X-Organization-Id";
    public static final String USER_HEADER = "X-User-Id";

    private static final String ORGANIZATION_MDC_KEY = "organizationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RequestContext context = readContext(request);
        if (context != null) {
            RequestContextHolder.set(context);
            MDC.put(ORGANIZATION_MDC_KEY, context.organizationId().toString());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            RequestContextHolder.clear();
            MDC.remove(ORGANIZATION_MDC_KEY);
        }
    }

    /** Construye el contexto; devuelve null cuando la ruta es publica o los headers son invalidos. */
    private RequestContext readContext(HttpServletRequest request) {
        UUID organizationId = parseUuid(request.getHeader(ORGANIZATION_HEADER));
        if (organizationId == null) {
            return null;
        }
        return new RequestContext(organizationId, parseUuid(request.getHeader(USER_HEADER)));
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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.startsWith("/internal");
    }
}
