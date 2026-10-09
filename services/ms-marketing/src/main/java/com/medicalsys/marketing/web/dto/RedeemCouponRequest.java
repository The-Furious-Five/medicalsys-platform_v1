package com.medicalsys.marketing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Canje efectivo de un cupon, pedido por ms-facturacion una vez emitida la factura.
 *
 * <p>{@code invoiceId} es la clave de idempotencia. Reintentar el mismo canje devuelve el canje ya
 * registrado en vez de consumir otro uso: la red puede perder la respuesta y el reintento es
 * legitimo.
 *
 * @param couponCode        codigo presentado por el paciente
 * @param patientId         paciente al que se le cobro
 * @param serviceOfferingId prestacion cobrada
 * @param baseAmountBob     importe sobre el que se calculo el descuento
 * @param invoiceId         factura que consumio el cupon; clave de idempotencia
 * @param appointmentId     cita asociada, si la hubo
 */
public record RedeemCouponRequest(
        @NotBlank(message = "no debe estar vacio")
        @Size(max = 30, message = "debe tener como maximo 30 caracteres")
        String couponCode,

        @NotNull(message = "es obligatorio")
        UUID patientId,

        UUID serviceOfferingId,

        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        BigDecimal baseAmountBob,

        @NotNull(message = "es obligatoria")
        UUID invoiceId,

        UUID appointmentId) {
}
