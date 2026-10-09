package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.RecordStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Cambio de estado administrativo del expediente.
 *
 * @param status         nuevo estado
 * @param retentionUntil plazo de conservacion legal, opcional
 */
public record ChangeRecordStatusRequest(
        @NotNull(message = "status es obligatorio") RecordStatus status,
        LocalDate retentionUntil) {
}
