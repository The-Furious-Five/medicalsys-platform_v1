package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.ResourceStatus;
import com.medicalsys.agenda.domain.ResourceType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ResourceResponse(
        UUID publicId,
        UUID organizationPublicId,
        UUID branchPublicId,
        String code,
        String name,
        ResourceType type,
        String floor,
        Short peopleCapacity,
        Short preparationMinutes,
        boolean requiresSterilization,
        String equipment,
        ResourceStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
