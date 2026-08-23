package com.medicalsys.facturacion.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Caja emisora solicitada explicitamente.
 *
 * <p>Es opcional: si no se envia, la factura sale por la primera caja activa de la organizacion. Se
 * identifica por el par sucursal/punto de venta porque asi lo hace el correlativo fiscal.
 *
 * @param taxBranchCode   codigo de sucursal del SIN; 0 es casa matriz
 * @param pointOfSaleCode codigo del punto de venta dentro de esa sucursal
 */
public record PointOfSaleRef(
        @NotNull @PositiveOrZero Integer taxBranchCode,
        @NotNull @PositiveOrZero Integer pointOfSaleCode) {
}
