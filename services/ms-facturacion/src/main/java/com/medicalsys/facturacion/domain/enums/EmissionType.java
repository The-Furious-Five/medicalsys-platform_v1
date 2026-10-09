package com.medicalsys.facturacion.domain.enums;

/**
 * Forma de emision ({@code invoice.emission_type}).
 *
 * <p>{@link #OFFLINE} exige un CAFC, lo garantiza el CHECK {@code ck_invoice_offline_requires_cafc}.
 * El MVP solo emite {@link #ONLINE}.
 */
public enum EmissionType {
    ONLINE,
    OFFLINE,
    BULK
}
