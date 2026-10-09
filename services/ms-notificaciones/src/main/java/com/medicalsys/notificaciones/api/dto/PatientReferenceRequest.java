package com.medicalsys.notificaciones.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record PatientReferenceRequest(
        @NotNull UUID patientId,
        @NotNull UUID organizationId,
        @NotBlank String displayName,
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String whatsappNumber,
        boolean active) {
}
