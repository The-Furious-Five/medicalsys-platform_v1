package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.AllergySeverity;
import com.medicalsys.historial.domain.enums.AllergyStatus;
import com.medicalsys.historial.domain.enums.AllergyType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Alergia registrada en el expediente.
 *
 * @param id             identificador local dentro del expediente
 * @param type           tipo de alergia
 * @param substance      sustancia responsable
 * @param medicationCode codigo del vademecum
 * @param reaction       reaccion observada
 * @param severity       severidad
 * @param status         vigencia
 * @param recordedById   profesional que registro la alergia
 * @param recordedAt     instante del registro
 */
public record AllergyResponse(
        Long id,
        AllergyType type,
        String substance,
        String medicationCode,
        String reaction,
        AllergySeverity severity,
        AllergyStatus status,
        UUID recordedById,
        OffsetDateTime recordedAt) {
}
