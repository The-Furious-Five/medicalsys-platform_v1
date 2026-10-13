package com.medicalsys.identidad.web;

import com.medicalsys.identidad.service.ApiException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Cuenta que ejecuta la peticion, tomada del JWT que ya valido JwtAuthenticationFilter. Nunca
 * devuelve {@code null}: para los servicios, un actor nulo es un proceso interno exento de la
 * segregacion de funciones, y una peticion web no puede quedar en ese caso.
 */
final class Actors {

    private Actors() {
    }

    static UUID publicId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
            return UUID.fromString(jwt.getSubject());
        }
        throw ApiException.unauthorized("Se requiere un token de acceso valido");
    }
}
