package com.medicalsys.pacientes.web.dto;

import com.medicalsys.pacientes.domain.enums.Department;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.PatientStatus;
import com.medicalsys.pacientes.domain.enums.Sex;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ficha del paciente.
 *
 * <p>{@code patientId} es el {@code public_id} de patients_db: el identificador con el que agenda,
 * historial, documental y facturacion referencian a esta persona.
 */
public record PatientResponse(
        UUID patientId,
        UUID userAccountId,
        DocumentType documentType,
        String documentNumber,
        String documentComplement,
        String givenNames,
        String paternalSurname,
        String maternalSurname,
        String fullName,
        LocalDate birthDate,
        Sex sex,
        String phoneE164,
        String alternatePhone,
        String email,
        String city,
        Department department,
        String emergencyContactName,
        String emergencyContactPhone,
        String emergencyContactRelationship,
        PatientStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
