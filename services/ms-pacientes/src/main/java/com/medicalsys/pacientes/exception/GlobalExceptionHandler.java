package com.medicalsys.pacientes.exception;

import com.medicalsys.pacientes.common.CorrelationIdFilter;
import com.medicalsys.pacientes.exception.ApiError.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
        if (exception.code().status().is5xxServerError()) {
            log.error("Error code={} path={}", exception.code(), request.getRequestURI(), exception);
        } else {
            log.warn("Solicitud rechazada code={} path={}", exception.code(), request.getRequestURI());
        }
        return build(exception.code(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldViolation> details = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        String.valueOf(violation.getPropertyPath()), violation.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR,
                "La solicitud no cumple las restricciones del contrato", request, details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        List<FieldViolation> details = List.of(
                new FieldViolation(exception.getName(), "Valor con formato invalido"));
        return build(ErrorCode.MALFORMED_REQUEST,
                "Un parametro de la ruta o consulta tiene formato invalido", request, details);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception,
            HttpServletRequest request) {
        log.warn("Violacion de integridad path={}", request.getRequestURI(), exception);
        return build(ErrorCode.DATA_INTEGRITY_CONFLICT,
                "La operacion viola una restriccion de unicidad o dominio", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Fallo inesperado path={}", request.getRequestURI(), exception);
        return build(ErrorCode.INTERNAL_ERROR,
                "Ocurrio un fallo inesperado al procesar la solicitud", request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> details = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldViolation(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return responseFor(ErrorCode.VALIDATION_ERROR,
                "La solicitud no cumple las restricciones del contrato", request, details);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        List<FieldViolation> details = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return responseFor(ErrorCode.VALIDATION_ERROR,
                "La solicitud no cumple las restricciones del contrato", request, details);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return responseFor(codeFor(status), messageFor(status), request, null);
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

    private ResponseEntity<ApiError> build(ErrorCode code, String message, HttpServletRequest request,
            List<FieldViolation> details) {
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(code, message, CorrelationIdFilter.current(request),
                        request.getRequestURI(), details));
    }

    private ResponseEntity<Object> responseFor(ErrorCode code, String message, WebRequest request,
            List<FieldViolation> details) {
        HttpServletRequest servletRequest = request instanceof ServletWebRequest wrapper
                ? wrapper.getRequest()
                : null;
        String path = servletRequest == null ? request.getDescription(false) : servletRequest.getRequestURI();
        String correlationId = servletRequest == null
                ? java.util.UUID.randomUUID().toString()
                : CorrelationIdFilter.current(servletRequest);
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(code, message, correlationId, path, details));
    }
}
