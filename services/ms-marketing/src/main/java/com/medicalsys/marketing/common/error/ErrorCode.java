package com.medicalsys.marketing.common.error;

import org.springframework.http.HttpStatus;

/**
 * Codigos de error estables del contrato de ms-marketing.
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

    /**
     * El plan del tenant no incluye marketing ({@code incluye_marketing = 0}).
     *
     * <p>Es el criterio C5 hecho respuesta HTTP: el modulo es opcional por contrato y un tenant que
     * no lo compro no debe poder usarlo aunque el servicio este desplegado.
     */
    MARKETING_NOT_INCLUDED_IN_PLAN(HttpStatus.FORBIDDEN),

    SEGMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND),
    PROMOTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    ADVERTISEMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    LOYALTY_PROGRAM_NOT_FOUND(HttpStatus.NOT_FOUND),
    PATIENT_NOT_KNOWN(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    SEGMENT_ALREADY_EXISTS(HttpStatus.CONFLICT),
    PROMOTION_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT),
    LOYALTY_PROGRAM_ALREADY_EXISTS(HttpStatus.CONFLICT),
    DATA_INTEGRITY_CONFLICT(HttpStatus.CONFLICT),

    /**
     * La frontera de privacidad del criterio C3.
     *
     * <p>Se devuelve cuando un segmento intenta filtrar por un eje que no es administrativo ni
     * demografico, o cuando un evento entrante trae un campo clinico. Cruzar patologia con
     * publicidad seria una filtracion de dato sensible de salud.
     */
    CLINICAL_DATA_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),

    SEGMENT_CRITERIA_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    CAMPAIGN_STATE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),
    CAMPAIGN_WITHOUT_AUDIENCE(HttpStatus.UNPROCESSABLE_ENTITY),
    PROMOTION_NOT_APPLICABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    PROMOTION_EXHAUSTED(HttpStatus.UNPROCESSABLE_ENTITY),
    ADVERTISEMENT_STATE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),
    LOYALTY_BALANCE_INSUFFICIENT(HttpStatus.UNPROCESSABLE_ENTITY),
    LOYALTY_PROGRAM_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    EVENT_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY),

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
