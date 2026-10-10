package com.medicalsys.documental.security;

import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Aplica {@link RequiresPermission} con los permisos que el gateway propaga en X-Permissions. */
@Component
public class PermissionAuthorizationInterceptor implements HandlerInterceptor {

    static final String PERMISSIONS_HEADER = "X-Permissions";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresPermission requirement = AnnotatedElementUtils.findMergedAnnotation(
                method.getMethod(), RequiresPermission.class);
        if (requirement == null) {
            return true;
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
