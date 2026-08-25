package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.BranchStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Cambio de estado de una sede.
 *
 * <p>Dar de baja una sucursal es pasarla a INACTIVE, nunca borrarla: citas y facturas ya emitidas
 * guardan su {@code public_id} como referencia externa.
 */
public record ChangeBranchStatusRequest(@NotNull BranchStatus status) {
}
