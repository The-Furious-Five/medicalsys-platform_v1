package com.medicalsys.marketing.domain.enums;

/** Tipo de asiento del libro mayor de puntos. */
public enum LoyaltyMovementType {
    /** Suma puntos por consumo facturado. */
    ACCRUAL,
    /** Resta puntos al canjearlos por un beneficio. */
    REDEMPTION,
    /** Resta puntos vencidos segun la vigencia del programa. */
    EXPIRATION,
    /** Ajuste registrado por una persona, con motivo obligatorio. */
    MANUAL_ADJUSTMENT,
    /** Suma puntos por traer a otro paciente. */
    REFERRAL_BONUS;

    /** Indica si el asiento debe restar puntos del saldo. */
    public boolean isDebit() {
        return this == REDEMPTION || this == EXPIRATION;
    }
}
