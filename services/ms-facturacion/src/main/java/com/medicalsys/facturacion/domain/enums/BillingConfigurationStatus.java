package com.medicalsys.facturacion.domain.enums;

/** Estado de la configuracion fiscal de una organizacion. */
public enum BillingConfigurationStatus {
    /** Datos incompletos: la organizacion todavia no puede emitir. */
    CONFIGURING,
    /** Configuracion utilizable para emitir documentos. */
    ACTIVE,
    /** Emision suspendida por decision administrativa. */
    SUSPENDED;

    public boolean allowsIssuing() {
        return this == ACTIVE;
    }
}
