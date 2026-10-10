package com.medicalsys.auditoria.common.security;

import com.medicalsys.auditoria.common.error.ForbiddenException;
import com.medicalsys.auditoria.common.error.UnauthenticatedException;
import com.medicalsys.auditoria.common.tenant.RequestContext;
import com.medicalsys.auditoria.common.tenant.RequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Aplica {@link RequiresPermission} antes de ejecutar el controlador. */
@Component
public class PermissionAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresPermission required = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(),
                RequiresPermission.class);
        if (required == null) {
            return true;
        }
        RequestContext context = RequestContextHolder.require();
        if (context.userId() == null) {
            throw new UnauthenticatedException("La identidad propagada por el gateway no incluye un X-User-Id valido");
        }
        if (!context.hasPermission(required.value())) {
            throw new ForbiddenException("La identidad autenticada no tiene el permiso " + required.value()
                    + " que exige esta consulta de la bitacora de auditoria");
        }
        return true;
    }
}
