package com.medicalsys.documental.security;

import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Decide el acceso a cada endpoint con los permisos que el gateway propaga en X-Permissions.
 *
 * <p>Se deniega por defecto (RBAC-04): un endpoint se ejecuta solo si declara
 * {@link RequiresPermission} y la identidad tiene ese permiso, o si se declara
 * {@link PublicEndpoint} o {@link InternalEndpoint}. Uno nuevo sin anotacion responde 403 hasta que
 * alguien decida que permiso exige.
 */
@Component
public class PermissionAuthorizationInterceptor implements HandlerInterceptor {

    static final String PERMISSIONS_HEADER = "X-Permissions";
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
            throw new ApiException(ErrorCode.FORBIDDEN, "Un endpoint interno solo se atiende bajo " + INTERNAL_PREFIX);
        }
        RequiresPermission requirement = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
        if (requirement == null) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "El endpoint no declara el permiso que exige y se deniega por defecto");
        }
        RequestIdentity.from(request);
        String header = request.getHeader(PERMISSIONS_HEADER);
        boolean granted = header != null && Arrays.stream(header.split(","))
                .map(String::trim)
                .anyMatch(requirement.value()::equals);
        if (!granted) {
            throw new ApiException(ErrorCode.FORBIDDEN, "La identidad autenticada no tiene el permiso "
                    + requirement.value() + " que exige esta operacion documental");
        }
        return true;
    }
}
