package com.medicalsys.facturacion.web.dto;

import com.medicalsys.facturacion.domain.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Proyeccion minima de una factura para listados y para otros microservicios.
 *
 * <p>No incluye el detalle ni los datos del cliente: ningun consumidor recibe mas de lo necesario.
 *
 * @param id            UUID publico de la factura
 * @param invoiceNumber correlativo dentro del punto de venta
 * @param patientId     paciente facturado
 * @param status        estado de la factura
 * @param demo          true cuando el documento no tiene validez fiscal
 * @param totalAmount   total del documento
 * @param issuedAt      instante de emision
 */
public record InvoiceSummaryResponse(
        UUID id,
        Long invoiceNumber,
        UUID patientId,
        InvoiceStatus status,
        boolean demo,
        BigDecimal totalAmount,
        OffsetDateTime issuedAt) {
}
