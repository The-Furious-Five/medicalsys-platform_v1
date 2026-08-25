package com.medicalsys.tenencia.common.error;

import org.springframework.http.HttpStatus;

/**
 * Codigos de error estables del contrato de ms-tenencia.
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

    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND),
    SERVICE_OFFERING_NOT_FOUND(HttpStatus.NOT_FOUND),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    SUBSCRIPTION_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    ORGANIZATION_CODE_ALREADY_USED(HttpStatus.CONFLICT),
    ORGANIZATION_NIT_ALREADY_USED(HttpStatus.CONFLICT),
    BRANCH_CODE_ALREADY_USED(HttpStatus.CONFLICT),
    SERVICE_OFFERING_CODE_ALREADY_USED(HttpStatus.CONFLICT),
    SUBSCRIPTION_ALREADY_ACTIVE(HttpStatus.CONFLICT),
    HEADQUARTERS_ALREADY_DEFINED(HttpStatus.CONFLICT),
    CONCURRENT_USAGE_UPDATE(HttpStatus.CONFLICT),
    DATA_INTEGRITY_CONFLICT(HttpStatus.CONFLICT),

    ORGANIZATION_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    SUBSCRIPTION_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    SUBSCRIPTION_PLAN_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    PLAN_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY),
    BRANCH_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_SUBSCRIPTION_PERIOD(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_USAGE_PERIOD(HttpStatus.UNPROCESSABLE_ENTITY),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
