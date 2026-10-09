package com.medicalsys.facturacion.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Prestacion a cobrar en una linea de la factura.
 *
 * <p>No lleva precio ni descripcion: los publica ms-tenencia. Quien emite la factura elige que se
 * cobra y cuanta cantidad, nunca a que precio.
 *
 * @param serviceOfferingId    prestacion del catalogo de la organizacion
 * @param quantity             cantidad facturada; por defecto 1
 * @param discountAmount       descuento aplicado a la linea; por defecto 0
 * @param appointmentId        cita que origina el cobro; trazabilidad opcional
 * @param clinicalEncounterId  encuentro que origina el cobro; trazabilidad opcional
 */
public record InvoiceLineRequest(
        @NotNull UUID serviceOfferingId,
        @DecimalMin(value = "0.0001") @Digits(integer = 8, fraction = 4) BigDecimal quantity,
        @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal discountAmount,
        UUID appointmentId,
        UUID clinicalEncounterId) {
}
