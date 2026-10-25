package com.medicalsys.identidad.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Con sesión, el titular sale del JWT y {@code username} se ignora. Sin sesión (contraseña temporal o
 * vencida: el login responde 403 PASSWORD_CHANGE_REQUIRED), {@code username} identifica la cuenta y la
 * contraseña actual la valida Keycloak (IAM-05).
 */
public record ChangePasswordRequest(
        @Size(max = 40) String username,
        @NotBlank String currentPassword,
        @NotBlank @Size(max = 200) String newPassword) {
}
