package com.medicalsys.tenencia.domain.enums;

/**
 * Estado del contrato entre una organizacion y su plan ({@code subscription.status}).
 *
 * <p>{@link #TRIAL} y {@link #ACTIVE} habilitan el servicio; {@link #PAST_DUE} indica mora y
 * {@link #CANCELLED} cierra el contrato.
 */
public enum SubscriptionStatus {

    /** Periodo de prueba; el diccionario la fija como estado inicial por defecto. */
    TRIAL,
    /** Contrato vigente y al dia. */
    ACTIVE,
    /** Contrato vigente con pago vencido. */
    PAST_DUE,
    /** Contrato terminado. */
    CANCELLED;

    /** Indica si el contrato habilita el uso del producto. */
    public boolean grantsService() {
        return this == TRIAL || this == ACTIVE;
    }

    /** Indica si el contrato sigue ocupando el lugar de la suscripcion vigente del tenant. */
    public boolean isCurrent() {
        return this != CANCELLED;
    }
}
