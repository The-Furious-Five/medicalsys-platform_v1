package com.medicalsys.pacientes.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MergePatientRequest(
        @NotNull UUID mergedPatientId,
        @NotBlank @Size(max = 500) String reason,
        @NotNull UUID eventId) {
}
