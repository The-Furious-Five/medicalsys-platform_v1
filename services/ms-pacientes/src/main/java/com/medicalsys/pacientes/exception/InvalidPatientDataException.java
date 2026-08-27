package com.medicalsys.pacientes.exception;

public class InvalidPatientDataException extends ApiException {

    public InvalidPatientDataException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
