package com.medicalsys.identidad.service;

import org.springframework.http.HttpStatus;

/** Error de negocio con estado HTTP explicito; lo traduce ApiExceptionHandler. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final String code;

    public ApiException(HttpStatus status, String title, String detail) {
        this(status, title, detail, null);
    }

    /** {@code code} es un identificador estable para el frontend, p. ej. PASSWORD_CHANGE_REQUIRED. */
    public ApiException(HttpStatus status, String title, String detail, String code) {
        super(detail);
        this.status = status;
        this.title = title;
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "Recurso no encontrado", detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Conflicto de estado", detail);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Solicitud invalida", detail);
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas", detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, "Acceso denegado", detail);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
