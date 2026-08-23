package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.RoleCode;
import java.time.LocalDate;
import java.util.UUID;

public record RoleAssignmentResponse(
        Long id,
        RoleCode role,
        UUID organizationPublicId,
        UUID branchPublicId,
        LocalDate validFrom,
        LocalDate validUntil,
        boolean active) {
}
