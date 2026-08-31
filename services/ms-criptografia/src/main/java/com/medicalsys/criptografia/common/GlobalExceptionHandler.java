package com.medicalsys.criptografia.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Map<String, String> MESSAGES = Map.of(
            "PLAINTEXT_REQUIRED", "El texto plano es obligatorio",
            "CIPHERTEXT_REQUIRED", "El ciphertext es obligatorio",
            "REQUESTING_SERVICE_REQUIRED", "requestingService es obligatorio",
            "CORRELATION_ID_REQUIRED", "correlationId es obligatorio",
            "ACTIVE_KEY_NOT_FOUND", "No hay una clave activa para el tenant y proposito solicitados",
            "DECRYPT_FAILED", "No fue posible descifrar el ciphertext con la clave indicada",
            "VALIDATION_ERROR", "La solicitud no supera las validaciones",
            "MALFORMED_REQUEST", "El cuerpo de la solicitud no es un JSON valido"
    );

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest req) {
        String code = ex.getReason() != null ? ex.getReason() : "ERROR";
        return ResponseEntity.status(ex.getStatusCode()).body(error(
                ex.getStatusCode().value(), code, MESSAGES.getOrDefault(code, code), req));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest req) {
        String code = ex instanceof HttpMessageNotReadableException ? "MALFORMED_REQUEST" : "VALIDATION_ERROR";
        return ResponseEntity.badRequest().body(error(400, code, MESSAGES.get(code), req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        return ResponseEntity.internalServerError().body(error(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "Error interno del servidor",
                req));
    }

    private static ErrorResponse error(int status, String code, String message, HttpServletRequest req) {
        return new ErrorResponse(
                Instant.now(),
                status,
                code,
                message,
                MDC.get(CorrelationIdFilter.MDC_KEY),
                req.getRequestURI()
        );
    }
}
