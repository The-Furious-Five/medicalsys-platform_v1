package com.medicalsys.identidad.service;

import org.springframework.http.HttpStatus;

/** Error de negocio con estado HTTP explicito; lo traduce ApiExceptionHandler. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    /** Codigo estable para que el cliente distinga el caso sin leer el texto; puede ser nulo. */
    private final String code;

    public ApiException(HttpStatus status, String title, String detail) {
        this(status, title, detail, null);
    }

    public ApiException(HttpStatus status, String title, String detail, String code) {
        super(detail);
        this.status = status;
        this.title = title;
        this.code = code;
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

    /** Bloqueo por fuerza bruta de Keycloak: solo un administrador lo levanta. */
    public static ApiException accountLocked() {
        return new ApiException(HttpStatus.LOCKED, "Cuenta bloqueada",
                "La cuenta esta bloqueada por intentos fallidos. Solicita el desbloqueo a un administrador.",
                "ACCOUNT_LOCKED");
    }

    /** Contrasena temporal o vencida: hay que cambiarla antes de entrar. */
    public static ApiException passwordChangeRequired() {
        return new ApiException(HttpStatus.FORBIDDEN, "Cambio de contrasena requerido",
                "Debes cambiar tu contrasena antes de iniciar sesion.", "PASSWORD_CHANGE_REQUIRED");
    }

    public static ApiException passwordPolicy(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Contrasena no permitida", detail,
                "PASSWORD_POLICY_VIOLATION");
    }

    public static ApiException identityProviderUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Proveedor de identidad no disponible",
                "No se pudo contactar a Keycloak. Intenta de nuevo en unos minutos.", "IDENTITY_PROVIDER_UNAVAILABLE");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getCode() {
        return code;
    }
}
