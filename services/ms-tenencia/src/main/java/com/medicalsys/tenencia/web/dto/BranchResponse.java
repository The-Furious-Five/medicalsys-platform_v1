package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.BranchStatus;
import com.medicalsys.tenencia.domain.enums.Department;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Ficha de una sede fisica. */
public record BranchResponse(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        boolean headquarters,
        String address,
        String city,
        Department department,
        String phoneE164,
        BigDecimal latitude,
        BigDecimal longitude,
        String openingHours,
        BranchStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
