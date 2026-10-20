package com.medicalsys.notificaciones.error;

import com.medicalsys.notificaciones.error.ApiError.FieldViolation;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException exception, ServerWebExchange exchange) {
        return response(exception.status(), exception.code(), exception.getMessage(), exchange, List.of());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> validation(WebExchangeBindException exception, ServerWebExchange exchange) {
        List<FieldViolation> details = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldViolation(
                        error instanceof FieldError field ? field.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud no cumple las restricciones del contrato", exchange, details);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiError> malformed(ServerWebInputException exception, ServerWebExchange exchange) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "La solicitud contiene un valor ausente o con formato invalido", exchange, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException exception, ServerWebExchange exchange) {
        log.warn("Violacion de integridad en {} corr={}", exchange.getRequest().getPath().value(),
                correlationId(exchange));
        return response(HttpStatus.CONFLICT, "DATA_INTEGRITY_CONFLICT",
                "La operacion viola una restriccion de unicidad o dominio", exchange, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception, ServerWebExchange exchange) {
        log.error("Fallo inesperado en {} corr={}", exchange.getRequest().getPath().value(),
                correlationId(exchange), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocurrio un fallo inesperado al procesar la solicitud", exchange, List.of());
    }

    private String correlationId(ServerWebExchange exchange) {
        return exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
            ServerWebExchange exchange, List<FieldViolation> details) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message,
                exchange.getRequest().getPath().value(), correlationId(exchange), details);
        return ResponseEntity.status(status).body(body);
    }
}
