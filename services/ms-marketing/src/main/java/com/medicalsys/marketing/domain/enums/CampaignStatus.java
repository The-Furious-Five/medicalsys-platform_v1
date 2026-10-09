package com.medicalsys.marketing.domain.enums;

/**
 * Ciclo de vida de la campana.
 *
 * <p>Las transiciones estan en un solo lugar para que ninguna ruta pueda, por ejemplo, relanzar una
 * campana cancelada o modificar la audiencia de una que ya se esta ejecutando.
 */
public enum CampaignStatus {
    DRAFT,
    SCHEDULED,
    RUNNING,
    PAUSED,
    FINISHED,
    CANCELLED;

    /** Indica si la campana admite que se recalcule su audiencia. */
    public boolean allowsAudienceRefresh() {
        return this == DRAFT || this == SCHEDULED;
    }

    /** Indica si desde este estado se puede lanzar. */
    public boolean allowsLaunch() {
        return this == DRAFT || this == SCHEDULED || this == PAUSED;
    }

    /** Estado terminal: ya no acepta ninguna transicion. */
    public boolean isTerminal() {
        return this == FINISHED || this == CANCELLED;
    }
}
