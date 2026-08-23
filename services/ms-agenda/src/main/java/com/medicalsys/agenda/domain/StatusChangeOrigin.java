package com.medicalsys.agenda.domain;

/** Quien provoco un cambio de estado de la cita. */
public enum StatusChangeOrigin {
    USER,
    PATIENT,
    SYSTEM,
    WHATSAPP
}
