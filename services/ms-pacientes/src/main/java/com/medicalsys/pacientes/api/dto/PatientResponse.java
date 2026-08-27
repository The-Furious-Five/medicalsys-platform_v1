package com.medicalsys.pacientes.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(
    UUID patientId,
    String documentType,
    String documentNumber,
    String documentComplement,
    String issuedDepartment,
    String givenNames,
    String paternalSurname,
    String maternalSurname,
    String fullName,
    LocalDate birthDate,
    String sex,
    String phoneE164,
    String email,
    String city,
    String department,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
