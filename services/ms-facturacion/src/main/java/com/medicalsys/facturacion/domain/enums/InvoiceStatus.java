package com.medicalsys.facturacion.domain.enums;

/**
 * Estado de una factura ({@code invoice.status}).
 *
 * <p>El diccionario no lista la columna entre los atributos de {@code invoice}, pero declara el
 * indice {@code ix_invoice_status_issued_at} y el CHECK que exige motivo y fecha al anular. Los
 * valores replican los de {@code credit_debit_note} y agregan {@link #DEMO_ACCEPTED}, que es el
 * estado que el sprint exige para una factura de demostracion.
 */
public enum InvoiceStatus {

    /** Emitida y persistida localmente, sin respuesta del sistema fiscal. */
    ISSUED,
    /** A la espera de envio al sistema fiscal. */
    SEND_PENDING,
    /**
     * Aceptada por el adaptador simulado del entorno local.
     *
     * <p>No afirma validez fiscal: la factura lleva siempre la leyenda no tributaria.
     */
    DEMO_ACCEPTED,
    /** Validada por el sistema fiscal real. Fuera del alcance de este sprint. */
    VALIDATED,
    /** Rechazada por el sistema fiscal. */
    REJECTED,
    /** Anulada. Exige motivo y fecha de anulacion. */
    VOIDED;

    /** Indica si el estado proviene del adaptador simulado y no de una autoridad fiscal real. */
    public boolean isDemo() {
        return this == DEMO_ACCEPTED;
    }
}
