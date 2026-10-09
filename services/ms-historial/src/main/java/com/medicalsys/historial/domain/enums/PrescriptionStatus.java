package com.medicalsys.historial.domain.enums;

/** Estado de la receta ({@code prescription.status}). */
public enum PrescriptionStatus {

    ISSUED,
    PARTIALLY_DISPENSED,
    DISPENSED,
    EXPIRED,
    VOIDED;

    /** Indica si la receta admite la transicion solicitada. */
    public boolean canTransitionTo(PrescriptionStatus target) {
        return switch (this) {
            case ISSUED -> target == PARTIALLY_DISPENSED || target == DISPENSED || target == EXPIRED
                    || target == VOIDED;
            case PARTIALLY_DISPENSED -> target == DISPENSED || target == EXPIRED || target == VOIDED;
            case DISPENSED, EXPIRED, VOIDED -> false;
        };
    }
}
