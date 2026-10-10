package com.medicalsys.identidad.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record RoleAssignmentRequest(
        @NotBlank @Size(max = 30) String role,
        UUID organizationPublicId,
        UUID branchPublicId,
        LocalDate validFrom,
        LocalDate validUntil) {
}
