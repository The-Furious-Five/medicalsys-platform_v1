package com.medicalsys.facturacion.common.error;

import org.springframework.http.HttpStatus;

/**
 * Codigos de error estables del contrato de ms-facturacion.
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

    INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND),
    POINT_OF_SALE_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    INVOICE_NUMBERING_CONFLICT(HttpStatus.CONFLICT),
    INVOICE_CANCELLATION_NOT_ALLOWED(HttpStatus.CONFLICT),
    DATA_INTEGRITY_CONFLICT(HttpStatus.CONFLICT),

    BILLING_CONFIGURATION_NOT_READY(HttpStatus.UNPROCESSABLE_ENTITY),
    POINT_OF_SALE_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    PATIENT_NOT_BILLABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    SERVICE_OFFERING_NOT_BILLABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    INVOICE_TOTALS_NOT_CONSISTENT(HttpStatus.UNPROCESSABLE_ENTITY),

    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
