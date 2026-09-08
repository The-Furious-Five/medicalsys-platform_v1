package com.medicalsys.marketing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Consulta de ms-facturacion antes de aplicar un cupon.
 *
 * <p>Es una operacion de solo lectura: no consume cupo. Existe separada del canje porque la caja
 * necesita mostrar el descuento antes de cobrar, y mostrar no puede gastar el cupon.
 *
 * @param couponCode        codigo presentado por el paciente
 * @param patientId         paciente al que se le cobraria
 * @param serviceOfferingId prestacion que se esta cobrando; puede faltar si el cobro es mixto
 * @param baseAmountBob     importe sobre el que se calcularia el descuento
 */
public record ValidateCouponRequest(
        @NotBlank(message = "no debe estar vacio")
        @Size(max = 30, message = "debe tener como maximo 30 caracteres")
        String couponCode,

        @NotNull(message = "es obligatorio")
        UUID patientId,

        UUID serviceOfferingId,

        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        BigDecimal baseAmountBob) {
}
