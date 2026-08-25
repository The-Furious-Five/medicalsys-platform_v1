package com.medicalsys.tenencia.domain.enums;

/**
 * Departamento de Bolivia ({@code organization.department} y {@code branch.department}).
 *
 * <p>Valores controlados del diccionario, capitulo 3. En organization es opcional; en branch es
 * obligatorio, porque una sede fisica siempre esta en algun lugar.
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
    PANDO
}
