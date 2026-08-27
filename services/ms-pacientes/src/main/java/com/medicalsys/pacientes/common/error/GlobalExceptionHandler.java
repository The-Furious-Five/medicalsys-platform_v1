package com.medicalsys.pacientes.common.error;

import com.medicalsys.pacientes.common.correlation.CorrelationId;
import com.medicalsys.pacientes.common.error.ApiError.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce toda excepcion al JSON de error comun del MVP.
 *
 * <p>Un 4xx esperado se registra como advertencia sin stack trace; un 5xx conserva la excepcion en
 * los logs internos y devuelve un mensaje generico al cliente (guia Sprint 2, seccion 10.5).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.code();
        if (code.status().is5xxServerError()) {
            log.error("Fallo interno code={} path={}", code, request.getRequestURI(), ex);
        } else {
            log.warn("Regla no cumplida code={} path={} detalle={}", code, request.getRequestURI(), ex.getMessage());
        }
        return build(code, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest request) {
        List<FieldViolation> details = ex.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(String.valueOf(violation.getPropertyPath()),
                        violation.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "La solicitud no cumple las restricciones del contrato",
                request.getRequestURI(), details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        List<FieldViolation> details = List.of(new FieldViolation(ex.getName(), "Valor con formato invalido"));
        return build(ErrorCode.MALFORMED_REQUEST, "Un parametro de la ruta o de la consulta tiene formato invalido",
                request.getRequestURI(), details);
    }

    /**
     * Traduce las restricciones que solo el motor puede comprobar.
     *
     * <p>Los codigos de unicidad concretos los levanta el servicio antes de escribir; este manejador
     * cubre la carrera entre dos solicitudes simultaneas y los CHECK del diccionario.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        log.warn("Violacion de integridad en {}", request.getRequestURI(), ex);
        return build(ErrorCode.DATA_INTEGRITY_CONFLICT,
                "La operacion viola una restriccion de unicidad o de dominio de pacientes",
                request.getRequestURI(), null);
    }

    /**
     * Conflicto de escritura sobre {@code plan_usage}.
     *
     * <p>La columna {@code version} del diccionario existe justamente para esto: dos incrementos
     * simultaneos de la misma cuota no pueden pisarse en silencio.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLocking(OptimisticLockingFailureException ex,
            HttpServletRequest request) {
        log.warn("Actualizacion concurrente en {}", request.getRequestURI(), ex);
        return build(ErrorCode.CONCURRENT_UPDATE,
                "Otra operacion modifico el mismo paciente al mismo tiempo; reintente la solicitud",
                request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Fallo inesperado en {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "Ocurrio un fallo inesperado al procesar la solicitud",
                request.getRequestURI(), null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> details = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldViolation(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return responseFor(ErrorCode.VALIDATION_ERROR, "La solicitud no cumple las restricciones del contrato",
                path(request), details);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> details = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return responseFor(ErrorCode.VALIDATION_ERROR, "La solicitud no cumple las restricciones del contrato",
                path(request), details);
    }

    /** Punto unico por el que pasan todas las excepciones estandar de Spring MVC. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        return responseFor(codeFor(statusCode), messageFor(statusCode), path(request), null);
    }

    private ErrorCode codeFor(HttpStatusCode status) {
        if (status.equals(HttpStatus.METHOD_NOT_ALLOWED)) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        if (status.equals(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        }
        if (status.equals(HttpStatus.NOT_FOUND)) {
            return ErrorCode.RESOURCE_NOT_FOUND;
        }
        if (status.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        return ErrorCode.MALFORMED_REQUEST;
    }

    private String messageFor(HttpStatusCode status) {
        if (status.equals(HttpStatus.METHOD_NOT_ALLOWED)) {
            return "El metodo HTTP no esta permitido en esta ruta";
        }
        if (status.equals(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return "El tipo de contenido enviado no esta soportado";
        }
        if (status.equals(HttpStatus.NOT_FOUND)) {
            return "La ruta solicitada no existe en ms-pacientes";
        }
        if (status.is5xxServerError()) {
            return "Ocurrio un fallo inesperado al procesar la solicitud";
        }
        return "La solicitud no pudo interpretarse";
    }

    private String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : request.getDescription(false);
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, String path,
            List<FieldViolation> details) {
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(code, message, CorrelationId.current(), path, details));
    }

    private ResponseEntity<Object> responseFor(ErrorCode code, String message, String path,
            List<FieldViolation> details) {
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(code, message, CorrelationId.current(), path, details));
    }
}
