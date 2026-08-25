package com.medicalsys.documental.security;

import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        RequiresRole requirement = AnnotatedElementUtils.findMergedAnnotation(
                method.getMethod(), RequiresRole.class);
        if (requirement == null) {
            requirement = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresRole.class);
        }
        if (requirement == null) {
            return true;
        }

        RequestIdentity.from(request);
        Set<Role> received = Arrays.stream(defaultString(request.getHeader("X-Roles")).split(","))
                .map(Role::parse)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        if (Arrays.stream(requirement.value()).noneMatch(received::contains)) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "El usuario no tiene un rol permitido para esta operacion");
        }
        return true;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
