package com.medicalsys.facturacion.domain.enums;

/** Estado de un punto de venta ({@code point_of_sale.status}). */
public enum PointOfSaleStatus {
    ACTIVE,
    CLOSED;

    public boolean allowsIssuing() {
        return this == ACTIVE;
    }
}
