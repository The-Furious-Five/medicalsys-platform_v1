package com.medicalsys.historial.domain.enums;

/**
 * Ciclo de vida del encuentro clinico ({@code clinical_encounter.status}).
 *
 * <p>Un encuentro se redacta como borrador, se firma y desde ese momento es inmutable. La unica
 * transicion posterior es la anulacion, que conserva la fila y su trazabilidad.
 */
public enum EncounterStatus {

    /** En redaccion: admite edicion de la narrativa y de sus hijos clinicos. */
    DRAFT,

    /** Firmado: el contenido queda congelado; una correccion se registra como addendum. */
    SIGNED,

    /** Anulado: conserva el registro con motivo y responsable. */
    VOIDED;

    /** Indica si el encuentro admite cambios en su contenido clinico. */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /** Indica si el encuentro admite la transicion solicitada. */
    public boolean canTransitionTo(EncounterStatus target) {
        return switch (this) {
            case DRAFT -> target == SIGNED || target == VOIDED;
            case SIGNED -> target == VOIDED;
            case VOIDED -> false;
        };
    }
}
