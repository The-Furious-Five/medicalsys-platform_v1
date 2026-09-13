package com.medicalsys.auditoria.common.correlation;

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
 * Propaga {@code X-Correlation-Id} entre saltos y lo publica en el MDC y en la respuesta.
 *
 * <p>Si el cliente no envia uno, el servicio genera un UUID para que el salto quede reconstruible.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = sanitize(request.getHeader(CorrelationId.HEADER));
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    /** Recorta y valida el valor recibido: un header hostil no debe contaminar los logs. */
    private String sanitize(String received) {
        if (received == null || received.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String trimmed = received.trim();
        if (trimmed.length() > MAX_LENGTH || !trimmed.chars().allMatch(CorrelationIdFilter::isAllowed)) {
            return UUID.randomUUID().toString();
        }
        return trimmed;
    }

    private static boolean isAllowed(int c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }
}
