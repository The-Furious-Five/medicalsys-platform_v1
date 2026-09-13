package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.RequiredResourceType;
import com.medicalsys.tenencia.domain.enums.ServiceOfferingType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Ficha completa de una prestacion del catalogo del tenant. */
public record ServiceOfferingResponse(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        String description,
        ServiceOfferingType type,
        String specialtyCode,
        Integer durationMinutes,
        BigDecimal basePriceBob,
        boolean requiresConsent,
        UUID consentTemplatePublicId,
        RequiredResourceType requiredResourceType,
        String taxProductCode,
        String taxUnitCode,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
