package com.medicalsys.pacientes.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmPatientAccessRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "el codigo debe tener 6 digitos") String otpCode) {
}
