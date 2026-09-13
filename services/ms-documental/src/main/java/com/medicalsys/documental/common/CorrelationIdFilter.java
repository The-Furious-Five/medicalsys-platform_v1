package com.medicalsys.documental.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".value";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = normalize(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        filterChain.doFilter(request, response);
    }

    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value == null ? normalize(request.getHeader(HEADER)) : value.toString();
    }

    private static String normalize(String candidate) {
        if (candidate != null) {
            try {
                return UUID.fromString(candidate.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // Un valor invalido se reemplaza para conservar trazabilidad.
            }
        }
        return UUID.randomUUID().toString();
    }
}
