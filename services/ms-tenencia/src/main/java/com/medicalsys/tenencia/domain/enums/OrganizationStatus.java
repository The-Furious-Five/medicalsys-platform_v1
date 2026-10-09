package com.medicalsys.tenencia.domain.enums;

/**
 * Estado del tenant ({@code organization.status}).
 *
 * <p>Una organizacion nace en {@link #ONBOARDING} y solo opera cuando esta {@link #ACTIVE}.
 */
public enum OrganizationStatus {

    /** Alta en curso: existe, pero todavia no opera. */
    ONBOARDING,
    /** Operativa. */
    ACTIVE,
    /** Suspendida, por ejemplo por falta de pago; conserva sus datos. */
    SUSPENDED,
    /** Baja definitiva. */
    CANCELLED;

    /** Indica si el tenant puede registrar sucursales, prestaciones y operar con normalidad. */
    public boolean isOperational() {
        return this == ACTIVE;
    }
}
