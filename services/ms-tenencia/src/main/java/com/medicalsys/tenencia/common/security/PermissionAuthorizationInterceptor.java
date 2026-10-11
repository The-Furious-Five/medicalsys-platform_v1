package com.medicalsys.tenencia.common.security;

import com.medicalsys.tenencia.common.error.ForbiddenException;
import com.medicalsys.tenencia.common.tenant.RequestContext;
import com.medicalsys.tenencia.common.tenant.RequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Decide el acceso a cada endpoint antes de ejecutar el controlador.
 *
 * <p>Se deniega por defecto (RBAC-04): un endpoint se ejecuta solo si declara
 * {@link RequiresPermission} y la identidad propagada por el gateway tiene ese permiso, o si se
 * declara {@link PublicEndpoint} o {@link InternalEndpoint}. Uno nuevo sin anotacion responde 403
 * hasta que alguien decida que permiso exige.
 */
@Component
public class PermissionAuthorizationInterceptor implements HandlerInterceptor {

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
        if (AnnotatedElementUtils.hasAnnotation(method, InternalEndpoint.class)) {
            if (request.getRequestURI().startsWith(INTERNAL_PREFIX)) {
                return true;
            }
            throw new ForbiddenException("Un endpoint interno solo se atiende bajo " + INTERNAL_PREFIX);
        }
        RequiresPermission required = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
        if (required == null) {
            throw new ForbiddenException("El endpoint no declara el permiso que exige y se deniega por defecto");
        }
        RequestContext context = RequestContextHolder.require();
        if (!context.hasPermission(required.value())) {
            throw new ForbiddenException("La identidad autenticada no tiene el permiso " + required.value()
                    + " que exige esta operacion de tenencia");
        }
        return true;
    }
}
