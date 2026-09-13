package com.medicalsys.historial.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Emision de una receta sobre un encuentro.
 *
 * @param validityDays        vigencia en dias; por defecto 30
 * @param generalInstructions indicaciones generales en claro; se persisten cifradas
 * @param items               detalles de la receta; al menos uno
 */
public record CreatePrescriptionRequest(
        @Min(1) @Max(365) Integer validityDays,
        @Size(max = 2000) String generalInstructions,
        @NotEmpty(message = "items debe contener al menos un medicamento") @Valid
        List<PrescriptionItemRequest> items) {
}
