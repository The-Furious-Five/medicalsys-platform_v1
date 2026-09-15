package com.medicalsys.criptografia.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Verifica que quien llama realmente conoce el secreto compartido antes de dejarlo cifrar o
 * descifrar nada. Sin esto, {@code requestingService} era un dato que el llamante se autoasignaba
 * (un literal fijo en el contrato con ms-historial, o un campo de texto libre en la API legacy) y
 * que ms-criptografia nunca comprobaba contra nada — cualquier proceso con acceso de red al puerto
 * 8084 podia cifrar o descifrar narrativa clinica de cualquier organizacion.
 */
@Component
public class ServiceAuthInterceptor implements HandlerInterceptor {

    public static final String SERVICE_NAME_HEADER = "X-Service-Name";
    public static final String SERVICE_SECRET_HEADER = "X-Service-Secret";
    public static final String VERIFIED_SERVICE_ATTRIBUTE = "com.medicalsys.criptografia.verifiedService";

    private final ServiceAuthProperties properties;

    public ServiceAuthInterceptor(ServiceAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String serviceName = request.getHeader(SERVICE_NAME_HEADER);
        if (serviceName == null || serviceName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SERVICE_AUTH_REQUIRED");
        }
        String secret = request.getHeader(SERVICE_SECRET_HEADER);
        if (secret == null || !constantTimeEquals(secret, properties.sharedSecret())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SERVICE_AUTH_INVALID");
        }
        request.setAttribute(VERIFIED_SERVICE_ATTRIBUTE, serviceName.trim());
        return true;
    }

    private boolean constantTimeEquals(String received, String expected) {
        return MessageDigest.isEqual(
                received.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
