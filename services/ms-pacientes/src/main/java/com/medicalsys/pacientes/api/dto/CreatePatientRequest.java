package com.medicalsys.pacientes.api.dto;

import com.medicalsys.pacientes.domain.Department;
import com.medicalsys.pacientes.domain.DocumentType;
import com.medicalsys.pacientes.domain.PatientSex;
import java.time.LocalDate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record CreatePatientRequest(
    @NotNull(message = "es obligatorio")
    DocumentType documentType,

    @NotBlank(message = "no debe estar vacio")
    @Size(max = 20, message = "debe tener como maximo 20 caracteres")
    String documentNumber,

    @Size(max = 5, message = "debe tener como maximo 5 caracteres")
    String documentComplement,

    Department issuedDepartment,

    @NotBlank(message = "no debe estar vacio")
    @Size(max = 90, message = "debe tener como maximo 90 caracteres")
    String givenNames,

    @NotBlank(message = "no debe estar vacio")
    @Size(max = 60, message = "debe tener como maximo 60 caracteres")
    String paternalSurname,

    @NotBlank(message = "no debe estar vacio")
    @Size(max = 60, message = "debe tener como maximo 60 caracteres")
    String maternalSurname,

    @NotNull(message = "es obligatoria")
    @PastOrPresent(message = "no puede estar en el futuro")
    LocalDate birthDate,

    PatientSex sex,

    @NotBlank(message = "no debe estar vacio")
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "debe usar el formato E.164")
    @Size(max = 20, message = "debe tener como maximo 20 caracteres")
    String phoneE164,

    @Email(message = "debe ser un correo valido")
    @Size(max = 100, message = "debe tener como maximo 100 caracteres")
    String email,

    @Size(max = 80, message = "debe tener como maximo 80 caracteres")
    String city,

    Department department
) {
}
