package com.medicalsys.criptografia.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Manejador global de excepciones para ms-criptografia.
 * Nunca expone stack traces al cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest req) {

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                ex.getStatusCode().value(),
                ex.getReason() != null ? ex.getReason() : "ERROR",
                ex.getMessage(),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                req.getRequestURI()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest req) {

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "Error interno del servidor",
                MDC.get(CorrelationIdFilter.MDC_KEY),
                req.getRequestURI()
        );
        return ResponseEntity.internalServerError().body(body);
    }
}
