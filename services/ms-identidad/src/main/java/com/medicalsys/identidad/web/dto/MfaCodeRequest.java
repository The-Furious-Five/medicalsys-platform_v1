package com.medicalsys.identidad.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Código de 6 dígitos de la aplicación autenticadora, con el token del desafío. */
public record MfaCodeRequest(
        @NotBlank @Size(max = 64) String mfaToken,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "debe tener 6 digitos") String code) {
}
