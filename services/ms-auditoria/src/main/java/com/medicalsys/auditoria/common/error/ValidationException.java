package com.medicalsys.auditoria.common.error;

/** Error de validación detectado después de deserializar el request. */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
