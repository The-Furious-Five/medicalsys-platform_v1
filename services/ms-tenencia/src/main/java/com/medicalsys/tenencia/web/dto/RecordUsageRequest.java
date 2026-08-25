package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.UsageMetric;
import jakarta.validation.constraints.NotNull;

/**
 * Reporte de consumo de una cuota.
 *
 * <p>Lo envia el servicio propietario del recurso consumido: identidad al alta de un usuario,
 * documental al guardar un archivo. Se acepta {@code delta} (incremento relativo) o
 * {@code value} (recuento absoluto tras una reconciliacion), pero no ambos: mezclar los dos
 * significa que el emisor no sabe cual es el valor correcto.
 *
 * @param metric cuota afectada
 * @param delta  incremento o decremento a aplicar
 * @param value  valor absoluto que reemplaza al materializado
 */
public record RecordUsageRequest(
        @NotNull UsageMetric metric,
        Long delta,
        Long value) {
}
