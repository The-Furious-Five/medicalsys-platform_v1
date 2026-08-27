package com.medicalsys.pacientes.security;

import com.medicalsys.pacientes.exception.ApiException;
import com.medicalsys.pacientes.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

        requireUuidHeader(request, "X-Organization-Id");
        requireUuidHeader(request, "X-User-Id");

        Set<Role> received = Arrays.stream(defaultString(request.getHeader("X-Roles")).split(","))
                .map(Role::parse)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        boolean allowed = Arrays.stream(requirement.value()).anyMatch(received::contains);
        if (!allowed) {
            throw new ApiException(ErrorCode.FORBIDDEN, "El usuario no tiene un rol permitido para esta operacion");
        }
        return true;
    }

    private void requireUuidHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED,
                    "La identidad validada por el gateway no fue propagada completamente");
        }
        try {
            UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED,
                    "La identidad propagada por el gateway tiene un formato invalido");
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
