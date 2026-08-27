package com.medicalsys.pacientes.exception;

public class PatientNotFoundException extends ApiException {

    public PatientNotFoundException() {
        super(ErrorCode.PATIENT_NOT_FOUND, "El paciente solicitado no existe");
    }
}
