package com.medicalsys.pacientes.exception;

public class DuplicatePatientDocumentException extends ApiException {

    private static final String MESSAGE = "Ya existe un paciente con el documento indicado";

    public DuplicatePatientDocumentException() {
        super(ErrorCode.PATIENT_DOCUMENT_ALREADY_EXISTS, MESSAGE);
    }

    public DuplicatePatientDocumentException(Throwable cause) {
        super(ErrorCode.PATIENT_DOCUMENT_ALREADY_EXISTS, MESSAGE, cause);
    }
}
