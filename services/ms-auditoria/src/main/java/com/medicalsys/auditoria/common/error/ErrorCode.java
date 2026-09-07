package com.medicalsys.auditoria.common.error;

import org.springframework.http.HttpStatus;

/**
 * Codigos de error estables del contrato de ms-auditoria.
 *
 * <p>El campo {@code code} es mas estable que {@code message}: las pruebas y los consumidores deben
 * reaccionar al codigo, nunca a una frase que puede cambiar o traducirse.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    AUDIT_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /**
     * El productor reintento un evento que ya fue asentado.
     *
     * <p>La bitacora es append-only: reescribir una entrada existente no es una opcion, asi que un
     * reintento con el mismo {@code eventId} se rechaza de forma explicita en vez de duplicar.
     */
    AUDIT_EVENT_ALREADY_RECORDED(HttpStatus.CONFLICT),
    DATA_INTEGRITY_CONFLICT(HttpStatus.CONFLICT),

    /** El rango de fechas pedido no es coherente (desde posterior a hasta, o ventana excesiva). */
    INVALID_AUDIT_RANGE(HttpStatus.UNPROCESSABLE_CONTENT),
    /** El evento trae contenido que la bitacora no puede almacenar (p. ej. metadatos enormes). */
    INVALID_AUDIT_PAYLOAD(HttpStatus.UNPROCESSABLE_CONTENT),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
