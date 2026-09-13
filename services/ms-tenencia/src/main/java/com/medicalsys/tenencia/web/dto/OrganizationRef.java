package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.OrganizationStatus;
import java.util.UUID;

/**
 * Proyeccion minima del tenant para consumo interno.
 *
 * <p>Un servicio vecino que valida un {@code organizationId} necesita saber que existe, como se
 * llama y si esta operativo. No necesita el NIT ni los datos de contacto, asi que no los recibe.
 */
public record OrganizationRef(
        UUID id,
        String code,
        String tradeName,
        OrganizationStatus status,
        String timeZone,
        String currency,
        boolean active) {
}
