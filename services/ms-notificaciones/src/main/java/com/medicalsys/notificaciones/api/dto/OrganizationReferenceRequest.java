package com.medicalsys.notificaciones.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrganizationReferenceRequest(
        @NotNull UUID organizationId,
        @NotBlank String legalName,
        boolean active) {
}
