package com.medicalsys.pacientes.common.error;

import org.springframework.http.HttpStatus;

/**
 * Codigos de error estables del contrato de ms-pacientes.
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

    PATIENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** La terna (tipo, numero, complemento) de documento ya pertenece a otro paciente. */
    PATIENT_DOCUMENT_ALREADY_EXISTS(HttpStatus.CONFLICT),
    PATIENT_USER_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT),
    PATIENT_ALREADY_MERGED(HttpStatus.CONFLICT),
    MERGE_EVENT_ALREADY_USED(HttpStatus.CONFLICT),
    DATA_INTEGRITY_CONFLICT(HttpStatus.CONFLICT),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT),

    PATIENT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_PATIENT_MERGE(HttpStatus.UNPROCESSABLE_CONTENT),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
