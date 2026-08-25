package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.Department;
import com.medicalsys.tenencia.domain.enums.OrganizationStatus;
import com.medicalsys.tenencia.domain.enums.OrganizationType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ficha completa del tenant.
 *
 * <p>{@code id} es el {@code public_id} de tenancy_db, no la clave numerica interna.
 */
public record OrganizationResponse(
        UUID id,
        String code,
        OrganizationType type,
        String legalName,
        String tradeName,
        String nit,
        String tradeRegistryNumber,
        String contactEmail,
        String phoneE164,
        String city,
        Department department,
        String timeZone,
        String currency,
        String logoUrl,
        OrganizationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
