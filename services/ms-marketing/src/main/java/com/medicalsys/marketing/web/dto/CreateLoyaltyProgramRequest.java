package com.medicalsys.marketing.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Alta de un programa de fidelizacion.
 *
 * @param name                 nombre del programa dentro de la organizacion
 * @param pointsPerBob         puntos que otorga cada boliviano facturado
 * @param bobPerPoint          valor en bolivianos de cada punto al canjearlo
 * @param pointsValidityMonths meses de vigencia de los puntos; nulo para que no caduquen
 */
public record CreateLoyaltyProgramRequest(
        @NotBlank(message = "no debe estar vacio")
        @Size(max = 120, message = "debe tener como maximo 120 caracteres")
        String name,

        @PositiveOrZero(message = "no puede ser negativo")
        BigDecimal pointsPerBob,

        @PositiveOrZero(message = "no puede ser negativo")
        BigDecimal bobPerPoint,

        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = 600, message = "debe ser como maximo 600")
        Integer pointsValidityMonths) {
}
