package com.medicalsys.pacientes.web.dto;

import com.medicalsys.pacientes.domain.enums.BloodType;
import com.medicalsys.pacientes.domain.enums.Department;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.MaritalStatus;
import com.medicalsys.pacientes.domain.enums.Sex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Datos admitidos al registrar un paciente; identificadores y auditoría los genera el servidor. */
public record RegisterPatientRequest(
        UUID userAccountId,
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 20) String documentNumber,
        @Size(max = 5) String documentComplement,
        Department issuedDepartment,
        @NotBlank @Size(max = 120) String givenNames,
        @NotBlank @Size(max = 80) String paternalSurname,
        @Size(max = 80) String maternalSurname,
        @NotNull @PastOrPresent LocalDate birthDate,
        Sex sex,
        MaritalStatus maritalStatus,
        BloodType bloodType,
        @Size(max = 120) String occupation,
        @Size(max = 20) @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "debe estar en formato E.164")
                String phoneE164,
        @Size(max = 20) @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "debe estar en formato E.164")
                String alternatePhone,
        @Email @Size(max = 160) String email,
        @Size(max = 1368) String encryptedAddressBase64,
        @Size(max = 80) String city,
        Department department,
        @Size(max = 160) String emergencyContactName,
        @Size(max = 20) @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "debe estar en formato E.164")
                String emergencyContactPhone,
        @Size(max = 60) String emergencyContactRelationship,
        UUID guardianPatientId,
        UUID encryptionKeyId,
        OffsetDateTime dataConsentAt) {
}
