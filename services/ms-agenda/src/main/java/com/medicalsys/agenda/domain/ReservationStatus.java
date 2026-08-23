package com.medicalsys.agenda.domain;

/** Estado de la ocupacion de un recurso fisico. */
public enum ReservationStatus {
    RESERVED,
    IN_USE,
    RELEASED,
    CANCELLED
}
