package com.medicalsys.facturacion.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Solicitud de emision de una factura a partir de un paciente y sus prestaciones.
 *
 * <p>El cuerpo <b>no</b> acepta {@code organizationId}: el tenant lo impone el gateway desde claims
 * firmados. Tampoco acepta importes ni datos del cliente: los resuelven ms-tenencia y ms-pacientes.
 *
 * @param patientId         paciente a facturar; se valida contra ms-pacientes
 * @param branchId          sucursal de la atencion; por defecto la del punto de venta
 * @param pointOfSale       caja emisora; por defecto la primera caja activa
 * @param paymentMethodCode metodo de pago del catalogo del SIN; 1 = efectivo
 * @param items             prestaciones a cobrar; al menos una
 */
public record CreateInvoiceRequest(
        @NotNull UUID patientId,
        UUID branchId,
        @Valid PointOfSaleRef pointOfSale,
        @Positive @Max(99) Integer paymentMethodCode,
        @NotEmpty @Size(max = 100) @Valid List<InvoiceLineRequest> items) {
}
