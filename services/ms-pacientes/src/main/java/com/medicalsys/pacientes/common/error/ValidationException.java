package com.medicalsys.pacientes.common.error;

/** Error de validación detectado después de deserializar el request. */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
