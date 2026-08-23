package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.RoleCode;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record RoleAssignmentRequest(
        @NotNull RoleCode role,
        UUID organizationPublicId,
        UUID branchPublicId,
        LocalDate validFrom,
        LocalDate validUntil) {
}
