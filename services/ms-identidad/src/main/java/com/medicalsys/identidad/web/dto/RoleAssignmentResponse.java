package com.medicalsys.identidad.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/** {@code active} indica si la asignacion da acceso hoy: vigente por fechas y con el rol activo. */
public record RoleAssignmentResponse(
        Long id,
        String role,
        UUID organizationPublicId,
        UUID branchPublicId,
        LocalDate validFrom,
        LocalDate validUntil,
        boolean active) {
}
