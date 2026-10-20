package com.medicalsys.profesionales.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicate(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violacion de integridad en {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("DUPLICATE_PROFESSIONAL", "La matrícula, usuario o documento ya está registrado"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException ex) {
        String code = ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
                ? "PROFESSIONAL_NOT_FOUND" : "INVALID_PROFESSIONAL_REQUEST";
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(code, ex.getReason()));
    }

    /**
     * Cualquier otra excepcion (NPE, errores de SQL, etc.) nunca debe exponer detalles internos
     * (stack trace, nombres de clase, mensajes de driver) al cliente HTTP; se registra completa
     * en el log del servidor y se responde solo un mensaje generico + codigo.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Fallo inesperado en {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Ocurrio un fallo inesperado al procesar la solicitud"));
    }
}
