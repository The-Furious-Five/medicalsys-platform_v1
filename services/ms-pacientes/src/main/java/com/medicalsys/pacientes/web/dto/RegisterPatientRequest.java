package com.medicalsys.pacientes.web.dto;

import com.medicalsys.pacientes.domain.enums.Department;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.enums.Sex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Alta de un paciente.
 *
 * <p>El {@code publicId} no viaja en el cuerpo: lo genera el servicio. La terna
 * (tipo, numero, complemento) de documento es unica en toda la base.
 *
 * @param documentComplement complemento del CI boliviano; ausente equivale a cadena vacia
 */
public record RegisterPatientRequest(
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 20) String documentNumber,
        @Size(max = 5) String documentComplement,
        @NotBlank @Size(max = 120) String givenNames,
        @NotBlank @Size(max = 80) String paternalSurname,
        @Size(max = 80) String maternalSurname,
        @NotNull @Past LocalDate birthDate,
        Sex sex,
        @Size(max = 20) @Pattern(regexp = "\\+?[0-9]{6,19}", message = "debe estar en formato E.164")
                String phoneE164,
        @Size(max = 20) @Pattern(regexp = "\\+?[0-9]{6,19}", message = "debe estar en formato E.164")
                String alternatePhone,
        @Email @Size(max = 160) String email,
        @Size(max = 80) String city,
        Department department,
        @Size(max = 160) String emergencyContactName,
        @Size(max = 20) String emergencyContactPhone,
        @Size(max = 60) String emergencyContactRelationship,
        UUID userAccountId) {
}
