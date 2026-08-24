package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.ResourceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateResourceRequest(
        @NotNull UUID organizationPublicId,
        @NotNull UUID branchPublicId,
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 120) String name,
        @NotNull ResourceType type,
        @Size(max = 20) String floor,
        @Min(1) Short peopleCapacity,
        @Min(0) Short preparationMinutes,
        Boolean requiresSterilization,
        String equipment) {
}
