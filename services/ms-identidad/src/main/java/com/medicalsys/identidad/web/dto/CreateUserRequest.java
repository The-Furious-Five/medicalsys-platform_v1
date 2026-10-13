package com.medicalsys.identidad.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 180) String fullName,
        // IAM-04: opcional. Sin ella se genera una contraseña temporal; con ella, Keycloak aplica la
        // política. En ambos casos el usuario debe cambiarla en su primer ingreso (UPDATE_PASSWORD).
        @Size(max = 64) String password,
        @Pattern(regexp = "^\\+[1-9][0-9]{6,18}$", message = "debe tener formato E.164") String phoneE164,
        UUID organizationPublicId,
        @Size(max = 5) String language,
        Boolean activateImmediately,
        @Valid List<RoleAssignmentRequest> roles) {
}
