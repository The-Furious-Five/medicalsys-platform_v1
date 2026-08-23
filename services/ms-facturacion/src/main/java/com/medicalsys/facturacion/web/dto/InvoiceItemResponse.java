package com.medicalsys.facturacion.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linea del detalle de la factura, con los valores congelados en la emision.
 *
 * @param itemNumber            posicion dentro de la factura
 * @param serviceOfferingId     prestacion facturada
 * @param description           descripcion congelada
 * @param quantity              cantidad facturada
 * @param unitPrice             precio unitario congelado
 * @param discountAmount        descuento aplicado
 * @param subtotal              importe de la linea: cantidad x precio - descuento
 * @param taxProductCode        codigo de producto/servicio homologado
 * @param unitOfMeasure         unidad de medida del catalogo del SIN
 * @param appointmentId         cita que origina el cobro, si se informo
 * @param clinicalEncounterId   encuentro que origina el cobro, si se informo
 */
public record InvoiceItemResponse(
        Integer itemNumber,
        UUID serviceOfferingId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal subtotal,
        String taxProductCode,
        Integer unitOfMeasure,
        UUID appointmentId,
        UUID clinicalEncounterId) {
}
