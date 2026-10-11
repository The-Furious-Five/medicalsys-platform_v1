package com.medicalsys.catalogos.common;

import com.medicalsys.catalogos.common.security.PermissionDeniedException;
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
import org.springframework.web.server.ResponseStatusException;

/**
 * Manejador global de excepciones.
 * Nunca expone stack traces al cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Map<String, String> MESSAGES = Map.of(
            "ICD10_NOT_FOUND", "El codigo CIE-10 no existe en el catalogo vigente",
            "STUDY_TYPE_NOT_FOUND", "El tipo de estudio no existe en el catalogo vigente",
            "MEDICATION_NOT_FOUND", "El medicamento no existe en el vademecum vigente",
            "SPECIALTY_NOT_FOUND", "La especialidad no existe en el catalogo vigente",
            "VALIDATION_ERROR", "La solicitud no supera las validaciones",
            "MALFORMED_REQUEST", "El cuerpo de la solicitud no es un JSON valido",
            "UNAUTHENTICATED", "La solicitud no incluye una identidad valida propagada por el gateway"
    );

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest req) {
        String code = ex.getReason() != null ? ex.getReason() : "ERROR";
        // Un 403 de autorizacion explica que permiso falta; el resto usa el mensaje de su codigo.
        String message = ex instanceof PermissionDeniedException ? ex.getBody().getDetail() : messageFor(code);
        return ResponseEntity.status(ex.getStatusCode()).body(error(
                ex.getStatusCode().value(), code, message, req));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest req) {
        String code = ex instanceof HttpMessageNotReadableException ? "MALFORMED_REQUEST" : "VALIDATION_ERROR";
        return ResponseEntity.badRequest().body(error(400, code, messageFor(code), req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        return ResponseEntity.internalServerError().body(error(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "Error interno del servidor",
                req));
    }

    private static String messageFor(String code) {
        return MESSAGES.getOrDefault(code, code);
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
