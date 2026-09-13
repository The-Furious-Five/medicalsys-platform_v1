package com.medicalsys.pacientes.domain.enums;

/**
 * Departamento de Bolivia.
 *
 * <p>Valores controlados del diccionario, capitulo 6. La columna es VARCHAR con CHECK en
 * patients_db, de modo que el motor rechaza cualquier valor fuera de esta lista.
 */
public enum Department {
    LA_PAZ,
    COCHABAMBA,
    SANTA_CRUZ,
    ORURO,
    POTOSI,
    CHUQUISACA,
    TARIJA,
    BENI,
    PANDO,
    FOREIGN;
}
