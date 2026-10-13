package com.medicalsys.criptografia.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Deniega por defecto (RBAC-04): solo se atienden los endpoints declarados {@link InternalEndpoint},
 * bajo /internal/, o {@link PublicEndpoint}.
 *
 * <p>ms-criptografia no tiene operaciones de negocio para el usuario final, asi que no hay permisos
 * del catalogo que comprobar: quien cifra o descifra es otro servicio, y eso lo verifica
 * {@link ServiceAuthInterceptor} con el secreto compartido. Este interceptor impide que un endpoint
 * nuevo quede expuesto sin que nadie haya decidido quien puede usarlo.
 */
@Component
public class EndpointPolicyInterceptor implements HandlerInterceptor {

    static final String INTERNAL_PREFIX = "/internal/";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Method method = handlerMethod.getMethod();
        if (AnnotatedElementUtils.hasAnnotation(method, PublicEndpoint.class)) {
            return true;
        }
        if (AnnotatedElementUtils.hasAnnotation(method, InternalEndpoint.class)
                && request.getRequestURI().startsWith(INTERNAL_PREFIX)) {
            return true;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ENDPOINT_NOT_DECLARED");
    }
}
