package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.RequiredResourceType;
import com.medicalsys.tenencia.domain.enums.ServiceOfferingType;
import java.util.UUID;

/**
 * Prestacion agendable publicada en la ruta interna para ms-agenda.
 *
 * <p>Lleva la duracion y el tipo de recurso necesarios para reservar el hueco, y omite el precio:
 * agenda no factura.
 */
public record SchedulableServiceResponse(
        UUID id,
        String code,
        String name,
        ServiceOfferingType type,
        String specialtyCode,
        Integer durationMinutes,
        RequiredResourceType requiredResourceType,
        boolean requiresConsent,
        UUID consentTemplatePublicId,
        boolean active) {
}
