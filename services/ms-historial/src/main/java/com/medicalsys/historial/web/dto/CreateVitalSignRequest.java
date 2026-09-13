package com.medicalsys.historial.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Medicion de signos vitales.
 *
 * <p>Todos los valores son opcionales: no se inventa un dato que no se tomo. El indice de masa
 * corporal no se envia; lo calcula la base a partir de peso y talla.
 *
 * @param measuredAt                instante de la toma; por defecto el actual
 * @param systolicPressure          presion sistolica en mmHg
 * @param diastolicPressure         presion diastolica en mmHg
 * @param heartRate                 frecuencia cardiaca en lpm
 * @param respiratoryRate           frecuencia respiratoria en rpm
 * @param temperatureC              temperatura en grados Celsius
 * @param oxygenSaturation          saturacion de oxigeno en porcentaje
 * @param bloodGlucoseMgDl          glucemia en mg/dl
 * @param weightKg                  peso en kilogramos
 * @param heightCm                  talla en centimetros
 * @param abdominalCircumferenceCm  perimetro abdominal en centimetros
 * @param painScale                 escala visual analogica de dolor, 0 a 10
 */
public record CreateVitalSignRequest(
        OffsetDateTime measuredAt,
        @Min(0) @Max(400) Integer systolicPressure,
        @Min(0) @Max(400) Integer diastolicPressure,
        @Min(0) @Max(400) Integer heartRate,
        @Min(0) @Max(200) Integer respiratoryRate,
        @DecimalMin("25.0") @DecimalMax("45.0") BigDecimal temperatureC,
        @Min(30) @Max(100) Integer oxygenSaturation,
        @Min(0) @Max(2000) Integer bloodGlucoseMgDl,
        @DecimalMin("0.1") @DecimalMax("500.00") BigDecimal weightKg,
        @DecimalMin("10.0") @DecimalMax("300.0") BigDecimal heightCm,
        @DecimalMin("10.0") @DecimalMax("300.0") BigDecimal abdominalCircumferenceCm,
        @Min(0) @Max(10) Integer painScale) {
}
