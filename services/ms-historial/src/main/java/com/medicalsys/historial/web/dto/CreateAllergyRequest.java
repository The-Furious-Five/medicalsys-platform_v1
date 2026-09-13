package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.AllergySeverity;
import com.medicalsys.historial.domain.enums.AllergyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de alergia en el expediente.
 *
 * @param type           tipo de alergia
 * @param substance      sustancia responsable
 * @param medicationCode codigo del vademecum si la sustancia esta catalogada
 * @param reaction       reaccion observada
 * @param severity       severidad; por defecto MILD
 */
public record CreateAllergyRequest(
        @NotNull(message = "type es obligatorio") AllergyType type,
        @NotBlank(message = "substance es obligatorio") @Size(max = 160) String substance,
        @Size(max = 30) String medicationCode,
        @Size(max = 300) String reaction,
        AllergySeverity severity) {
}
