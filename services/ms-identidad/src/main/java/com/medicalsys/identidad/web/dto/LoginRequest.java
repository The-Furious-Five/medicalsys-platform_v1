package com.medicalsys.identidad.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** IAM-07: se inicia sesión con el nombre de usuario estándar, no con el correo. */
public record LoginRequest(
        @NotBlank @Size(max = 40) String username,
        @NotBlank @Size(min = 8, max = 200) String password) {
}
