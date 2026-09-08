package com.medicalsys.marketing.domain.enums;

/** Resultado individual de un destinatario dentro de la campana. */
public enum RecipientStatus {
    SELECTED,
    EXCLUDED_OPT_OUT,
    SENT,
    READ,
    CONVERTED,
    FAILED;

    /** Un excluido por opt-out nunca cuenta como destinatario alcanzable. */
    public boolean isReachable() {
        return this != EXCLUDED_OPT_OUT && this != FAILED;
    }
}
