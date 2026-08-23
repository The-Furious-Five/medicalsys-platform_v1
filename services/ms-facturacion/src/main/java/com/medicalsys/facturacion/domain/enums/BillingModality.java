package com.medicalsys.facturacion.domain.enums;

/** Modalidad de facturacion declarada por la organizacion ({@code billing_configuration.modality}). */
public enum BillingModality {
    /** Facturacion electronica en linea. */
    ONLINE_ELECTRONIC,
    /** Facturacion computarizada en linea. Es la modalidad del entorno de demostracion. */
    ONLINE_COMPUTERIZED,
    /** Facturacion manual. */
    MANUAL
}
