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
        @NotBlank @Size(min = 8, max = 200) String password,
        @Pattern(regexp = "^\\+[1-9][0-9]{6,18}$", message = "debe tener formato E.164") String phoneE164,
        UUID organizationPublicId,
        @Size(max = 5) String language,
        Boolean activateImmediately,
        @Valid List<RoleAssignmentRequest> roles) {
}
