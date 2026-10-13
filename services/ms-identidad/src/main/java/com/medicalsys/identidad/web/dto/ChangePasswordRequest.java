package com.medicalsys.identidad.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Con sesión, el titular sale del JWT y {@code email} se ignora. Sin sesión (contraseña temporal o
 * vencida: el login responde 403 PASSWORD_CHANGE_REQUIRED), {@code email} identifica la cuenta y la
 * contraseña actual la valida Keycloak (IAM-05).
 */
public record ChangePasswordRequest(
        @Email @Size(max = 160) String email,
        @NotBlank String currentPassword,
        @NotBlank @Size(max = 200) String newPassword) {
}
