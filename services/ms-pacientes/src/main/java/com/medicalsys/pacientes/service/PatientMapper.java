package com.medicalsys.pacientes.service;

import com.medicalsys.pacientes.api.dto.BillingPatientSummary;
import com.medicalsys.pacientes.api.dto.PatientResponse;
import com.medicalsys.pacientes.api.dto.SchedulingPatientSummary;
import com.medicalsys.pacientes.domain.Patient;
import org.springframework.stereotype.Component;

@Component
public class PatientMapper {

    public PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getPublicId(),
                patient.getDocumentType().name(),
                patient.getDocumentNumber(),
                patient.getDocumentComplement(),
                name(patient.getIssuedDepartment()),
                patient.getGivenNames(),
                patient.getPaternalSurname(),
                patient.getMaternalSurname(),
                patient.getFullName(),
                patient.getBirthDate(),
                patient.getSex().name(),
                patient.getPhoneE164(),
                patient.getEmail(),
                patient.getCity(),
                name(patient.getDepartment()),
                patient.getStatus().name(),
                patient.getCreatedAt(),
                patient.getUpdatedAt());
    }

    public SchedulingPatientSummary toSchedulingSummary(Patient patient) {
        return new SchedulingPatientSummary(
                patient.getPublicId(), patient.getFullName(), patient.getStatus().name());
    }

    public BillingPatientSummary toBillingSummary(Patient patient) {
        return new BillingPatientSummary(
                patient.getPublicId(),
                patient.getFullName(),
                patient.getDocumentType().name(),
                patient.getDocumentNumber(),
                patient.getDocumentComplement(),
                patient.getStatus().name());
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
