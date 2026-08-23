package com.medicalsys.historial.domain.enums;

/**
 * Ciclo de vida de la orden de estudio ({@code study_order.status}).
 *
 * <p>Equivalencias con el lenguaje de negocio del sprint: {@code REQUESTED} es "solicitada" y
 * {@code REPORTED} es "disponible" (existe un resultado con su referencia documental).
 */
public enum StudyOrderStatus {

    /** Solicitada por el profesional durante el encuentro. */
    REQUESTED,

    /** Con cita de toma agendada en ms-agenda. */
    SCHEDULED,

    /** Muestra tomada. */
    SAMPLE_COLLECTED,

    /** En proceso de lectura o informe. */
    IN_PROGRESS,

    /** Informada: existe resultado y referencia al documento. */
    REPORTED,

    /** Anulada. */
    VOIDED;

    /** Indica si la orden admite la transicion solicitada. */
    public boolean canTransitionTo(StudyOrderStatus target) {
        return switch (this) {
            case REQUESTED -> target == SCHEDULED || target == SAMPLE_COLLECTED || target == VOIDED;
            case SCHEDULED -> target == SAMPLE_COLLECTED || target == IN_PROGRESS || target == VOIDED;
            case SAMPLE_COLLECTED -> target == IN_PROGRESS || target == REPORTED || target == VOIDED;
            case IN_PROGRESS -> target == REPORTED || target == VOIDED;
            case REPORTED, VOIDED -> false;
        };
    }

    /** Indica si aun puede registrarse un resultado sobre la orden. */
    public boolean acceptsResults() {
        return this != VOIDED;
    }
}
