package com.medicalsys.historial.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Medicion de signos vitales.
 *
 * @param id                       identificador local
 * @param measuredAt               instante de la toma
 * @param systolicPressure         presion sistolica en mmHg
 * @param diastolicPressure        presion diastolica en mmHg
 * @param heartRate                frecuencia cardiaca en lpm
 * @param respiratoryRate          frecuencia respiratoria en rpm
 * @param temperatureC             temperatura en grados Celsius
 * @param oxygenSaturation         saturacion de oxigeno en porcentaje
 * @param bloodGlucoseMgDl         glucemia en mg/dl
 * @param weightKg                 peso en kilogramos
 * @param heightCm                 talla en centimetros
 * @param bmi                      indice de masa corporal calculado por la base
 * @param abdominalCircumferenceCm perimetro abdominal en centimetros
 * @param painScale                escala de dolor
 * @param recordedById             usuario que registro la medicion
 */
public record VitalSignResponse(
        Long id,
        OffsetDateTime measuredAt,
        Integer systolicPressure,
        Integer diastolicPressure,
        Integer heartRate,
        Integer respiratoryRate,
        BigDecimal temperatureC,
        Integer oxygenSaturation,
        Integer bloodGlucoseMgDl,
        BigDecimal weightKg,
        BigDecimal heightCm,
        BigDecimal bmi,
        BigDecimal abdominalCircumferenceCm,
        Integer painScale,
        UUID recordedById) {
}
