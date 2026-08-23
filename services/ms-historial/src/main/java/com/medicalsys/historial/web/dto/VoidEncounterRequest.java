package com.medicalsys.historial.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Anulacion de un encuentro conservando el registro.
 *
 * @param reason motivo de la anulacion; es obligatorio para la trazabilidad
 */
public record VoidEncounterRequest(
        @NotBlank(message = "reason es obligatorio") @Size(max = 300) String reason) {
}
