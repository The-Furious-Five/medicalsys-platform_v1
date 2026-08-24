package com.medicalsys.identidad.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Escribe los errores 401 y 403 como ProblemDetail en la misma respuesta.
 *
 * <p>Usar response.sendError provocaria un reenvio a /error que vuelve a pasar por la cadena de
 * seguridad sin autenticacion, y un 403 terminaria devolviendose al cliente como 401.
 */
@Component
public class SecurityProblemWriter {

    private final ObjectMapper objectMapper;

    public SecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("service", "ms-identidad");
        String correlationId = response.getHeader("X-Correlation-Id");
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
