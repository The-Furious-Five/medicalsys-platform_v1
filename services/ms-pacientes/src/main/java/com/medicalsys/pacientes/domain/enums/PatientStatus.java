package com.medicalsys.pacientes.domain.enums;

/**
 * Estado del registro del paciente.
 *
 * <p>Valores controlados del diccionario, capitulo 6. La columna es VARCHAR con CHECK en
 * patients_db, de modo que el motor rechaza cualquier valor fuera de esta lista.
 */
public enum PatientStatus {
    ACTIVE,
    INACTIVE,
    DECEASED;
}
