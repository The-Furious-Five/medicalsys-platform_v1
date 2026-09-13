package com.medicalsys.tenencia.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Activacion o desactivacion de una prestacion.
 *
 * <p>Desactivar no borra: ms-facturacion rechaza facturar una prestacion inactiva, pero las
 * facturas anteriores conservan su detalle.
 */
public record ChangeServiceOfferingActiveRequest(@NotNull Boolean active) {
}
