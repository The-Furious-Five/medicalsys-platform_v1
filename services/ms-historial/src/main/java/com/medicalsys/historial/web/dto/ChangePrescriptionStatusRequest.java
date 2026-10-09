package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.PrescriptionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Cambio de estado de la receta.
 *
 * @param status nuevo estado
 */
public record ChangePrescriptionStatusRequest(
        @NotNull(message = "status es obligatorio") PrescriptionStatus status) {
}
