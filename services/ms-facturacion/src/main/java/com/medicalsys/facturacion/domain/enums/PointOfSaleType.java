package com.medicalsys.facturacion.domain.enums;

/** Tipo de punto de venta fiscal ({@code point_of_sale.type}). */
public enum PointOfSaleType {
    COMMISSION_AGENT,
    /** Caja de cobro. Es el tipo que usa la recepcion de la clinica. */
    COLLECTION_DESK,
    MOBILE,
    YPFB,
    ATM,
    OTHER
}
