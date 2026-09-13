package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.StudyOrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Transicion de estado de la orden de estudio.
 *
 * @param status nuevo estado
 */
public record ChangeStudyOrderStatusRequest(
        @NotNull(message = "status es obligatorio") StudyOrderStatus status) {
}
