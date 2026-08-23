package com.medicalsys.historial.common.security;

import com.medicalsys.historial.common.error.ForbiddenException;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.common.tenant.RequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Aplica {@link RequiresRole} antes de ejecutar el controlador. */
@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresRole required = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequiresRole.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequiresRole.class);
        }
        if (required == null) {
            return true;
        }
        RequestContext context = RequestContextHolder.require();
        if (Arrays.stream(required.value()).noneMatch(context::hasRole)) {
            throw new ForbiddenException(
                    "El rol de la identidad autenticada no permite ejecutar esta operacion clinica");
        }
        return true;
    }
}
