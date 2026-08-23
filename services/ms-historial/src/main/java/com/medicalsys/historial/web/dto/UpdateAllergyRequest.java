package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.AllergySeverity;
import com.medicalsys.historial.domain.enums.AllergyStatus;
import jakarta.validation.constraints.Size;

/**
 * Reevaluacion de una alergia; los campos nulos no se modifican.
 *
 * @param severity nueva severidad
 * @param reaction nueva descripcion de la reaccion
 * @param status   nueva vigencia
 */
public record UpdateAllergyRequest(
        AllergySeverity severity,
        @Size(max = 300) String reaction,
        AllergyStatus status) {
}
