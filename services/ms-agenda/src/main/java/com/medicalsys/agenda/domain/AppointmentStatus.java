package com.medicalsys.agenda.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de una cita y sus transiciones validas.
 *
 * <p>Los estados terminales no vuelven atras: una cita atendida, cancelada, no asistida o
 * reprogramada solo se corrige creando una cita nueva.
 */
public enum AppointmentStatus {
    REQUESTED,
    CONFIRMED,
    WAITING,
    IN_PROGRESS,
    ATTENDED,
    CANCELLED,
    NO_SHOW,
    RESCHEDULED;

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> TRANSITIONS = Map.of(
            REQUESTED, EnumSet.of(CONFIRMED, WAITING, CANCELLED, NO_SHOW, RESCHEDULED),
            CONFIRMED, EnumSet.of(WAITING, IN_PROGRESS, CANCELLED, NO_SHOW, RESCHEDULED),
            WAITING, EnumSet.of(IN_PROGRESS, CANCELLED, NO_SHOW),
            IN_PROGRESS, EnumSet.of(ATTENDED, CANCELLED),
            ATTENDED, Collections.emptySet(),
            CANCELLED, Collections.emptySet(),
            NO_SHOW, Collections.emptySet(),
            RESCHEDULED, Collections.emptySet());

    /** Estados que siguen ocupando la agenda del profesional (los que vigila la restriccion EXCLUDE). */
    public static final Set<AppointmentStatus> BLOCKING =
            Collections.unmodifiableSet(EnumSet.of(REQUESTED, CONFIRMED, WAITING, IN_PROGRESS, ATTENDED, NO_SHOW));

    public boolean canMoveTo(AppointmentStatus target) {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    public boolean isFinal() {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).isEmpty();
    }

    public Set<AppointmentStatus> allowedTransitions() {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet());
    }
}
