package com.medicalsys.pacientes.domain.enums;

/**
 * Tipo de documento de identidad del paciente.
 *
 * <p>Valores controlados del diccionario, capitulo 6. La columna es VARCHAR con CHECK en
 * patients_db, de modo que el motor rechaza cualquier valor fuera de esta lista.
 */
public enum DocumentType {
    CI,
    FOREIGN_ID_CARD,
    PASSPORT,
    BIRTH_CERTIFICATE,
    NO_DOCUMENT;
}
