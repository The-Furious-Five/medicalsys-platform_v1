package com.medicalsys.notificaciones.security;

import com.medicalsys.notificaciones.common.CorrelationIdWebFilter;
import com.medicalsys.notificaciones.common.RequestOrganization;
import com.medicalsys.notificaciones.error.ApiError;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Decide el acceso a cada endpoint antes de invocar el controlador.
 *
 * <p>Se deniega por defecto (RBAC-04): un endpoint se ejecuta solo si declara
 * {@link RequiresPermission} y la identidad propagada por el gateway tiene ese permiso, o si se
 * declara {@link PublicEndpoint} o {@link InternalEndpoint}. Uno nuevo sin anotacion responde 403
 * hasta que alguien decida que permiso exige.
 *
 * <p>Este servicio es reactivo y no tiene HandlerInterceptor como los demas: el filtro resuelve el
 * controlador con el mismo RequestMappingHandlerMapping que despues usa el DispatcherHandler. Una
 * ruta que no corresponde a ningun controlador sigue de largo y termina en 404.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PermissionAuthorizationWebFilter implements WebFilter {

    public static final String USER_HEADER = "X-User-Id";
    public static final String PERMISSIONS_HEADER = "X-Permissions";
    static final String INTERNAL_PREFIX = "/internal/";

    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectMapper objectMapper;

    public PermissionAuthorizationWebFilter(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            ObjectMapper objectMapper) {
        this.handlerMapping = handlerMapping;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return handlerMapping.getHandler(exchange)
                .ofType(HandlerMethod.class)
                .mapNotNull(handler -> denial(handler.getMethod(), exchange.getRequest()))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(denial -> denial.isPresent() ? reject(exchange, denial.get()) : chain.filter(exchange));
    }

    /** Por que se rechaza la solicitud, o {@code null} si puede llegar al controlador. */
    Denial denial(Method method, ServerHttpRequest request) {
        if (AnnotatedElementUtils.hasAnnotation(method, PublicEndpoint.class)) {
            return null;
        }
        if (AnnotatedElementUtils.hasAnnotation(method, InternalEndpoint.class)) {
            return request.getPath().value().startsWith(INTERNAL_PREFIX)
                    ? null
                    : Denial.forbidden("Un endpoint interno solo se atiende bajo " + INTERNAL_PREFIX);
        }
        RequiresPermission required = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
        if (required == null) {
            return Denial.forbidden("El endpoint no declara el permiso que exige y se deniega por defecto");
        }
        HttpHeaders headers = request.getHeaders();
        if (isBlank(headers.getFirst(USER_HEADER)) || isBlank(headers.getFirst(RequestOrganization.HEADER))) {
            return new Denial(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                    "La solicitud no incluye una identidad valida propagada por el gateway");
        }
        if (!granted(headers.getFirst(PERMISSIONS_HEADER), required.value())) {
            return Denial.forbidden("La identidad autenticada no tiene el permiso " + required.value()
                    + " que exige esta operacion de notificaciones");
        }
        return null;
    }

    /** Responde con el mismo ApiError que GlobalExceptionHandler, que aqui todavia no interviene. */
    private Mono<Void> reject(ServerWebExchange exchange, Denial denial) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(denial.status());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ApiError body = new ApiError(Instant.now(), denial.status().value(), denial.status().getReasonPhrase(),
                denial.code(), denial.message(), exchange.getRequest().getPath().value(),
                response.getHeaders().getFirst(CorrelationIdWebFilter.HEADER), List.of());
        return response.writeWith(Mono.just(response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body))));
    }

    private static boolean granted(String header, String permission) {
        return header != null && Arrays.stream(header.split(","))
                .map(String::trim)
                .anyMatch(permission::equals);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record Denial(HttpStatus status, String code, String message) {

        static Denial forbidden(String message) {
            return new Denial(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
    }
}
