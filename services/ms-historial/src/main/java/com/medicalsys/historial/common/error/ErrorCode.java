package com.medicalsys.historial.common.error;

import org.springframework.http.HttpStatus;

/**
 * Codigos de error estables del contrato de ms-historial.
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

    MEDICAL_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND),
    CLINICAL_ENCOUNTER_NOT_FOUND(HttpStatus.NOT_FOUND),
    ALLERGY_NOT_FOUND(HttpStatus.NOT_FOUND),
    MEDICAL_BACKGROUND_NOT_FOUND(HttpStatus.NOT_FOUND),
    PRESCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    STUDY_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    MEDICAL_RECORD_ALREADY_EXISTS(HttpStatus.CONFLICT),
    ENCOUNTER_ALREADY_EXISTS_FOR_APPOINTMENT(HttpStatus.CONFLICT),
    ALLERGY_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    DIAGNOSIS_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    STUDY_RESULT_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    NUMBERING_CONFLICT(HttpStatus.CONFLICT),
    DATA_INTEGRITY_CONFLICT(HttpStatus.CONFLICT),

    ENCOUNTER_NOT_EDITABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    ENCOUNTER_NOT_SIGNABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    ENCOUNTER_NOT_SIGNED(HttpStatus.UNPROCESSABLE_ENTITY),
    MEDICAL_RECORD_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    STUDY_ORDER_TRANSITION_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),
    PRESCRIPTION_TRANSITION_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),
    ICD10_CODE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),
    STUDY_TYPE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),
    MEDICATION_CODE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),

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
