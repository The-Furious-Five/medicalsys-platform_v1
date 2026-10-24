package com.medicalsys.identidad.domain;

/**
 * Escala MAC (ISO-01): clasificacion de cada permiso y nivel de autorizacion de cada cuenta. El
 * orden de declaracion es el orden de la escala, de menor a mayor.
 */
public enum SecurityLevel {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED;

    /** Una autorizacion cubre su nivel y todos los inferiores (no read up). */
    public boolean covers(SecurityLevel classification) {
        return compareTo(classification) >= 0;
    }
}
