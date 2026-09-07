package com.medicalsys.pacientes.web.dto;

import com.medicalsys.pacientes.domain.enums.BloodType;
import com.medicalsys.pacientes.domain.enums.Department;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.MaritalStatus;
import com.medicalsys.pacientes.domain.enums.PatientStatus;
import com.medicalsys.pacientes.domain.enums.Sex;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Representación pública completa de patient; nunca expone el BIGINT interno. */
public record PatientResponse(
        UUID patientId,
        UUID userAccountId,
        DocumentType documentType,
        String documentNumber,
        String documentComplement,
        Department issuedDepartment,
        String givenNames,
        String paternalSurname,
        String maternalSurname,
        String fullName,
        LocalDate birthDate,
        Sex sex,
        MaritalStatus maritalStatus,
        BloodType bloodType,
        String occupation,
        String phoneE164,
        String alternatePhone,
        String email,
        String encryptedAddressBase64,
        String city,
        Department department,
        String emergencyContactName,
        String emergencyContactPhone,
        String emergencyContactRelationship,
        UUID guardianPatientId,
        UUID encryptionKeyId,
        OffsetDateTime dataConsentAt,
        PatientStatus status,
        LocalDate deathDate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
