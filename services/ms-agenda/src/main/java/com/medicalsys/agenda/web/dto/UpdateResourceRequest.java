package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.ResourceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateResourceRequest(
        @Size(max = 120) String name,
        ResourceType type,
        @Size(max = 20) String floor,
        @Min(1) Short peopleCapacity,
        @Min(0) Short preparationMinutes,
        Boolean requiresSterilization,
        String equipment) {
}
