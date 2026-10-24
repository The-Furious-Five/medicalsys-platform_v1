package com.medicalsys.facturacion.web.dto;

import com.medicalsys.facturacion.domain.enums.EmissionType;
import com.medicalsys.facturacion.domain.enums.InvoiceStatus;
import com.medicalsys.facturacion.domain.enums.TaxDocumentType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Factura completa con su detalle.
 *
 * <p>{@code demo} y {@code caption} son parte del contrato, no un adorno: cualquier consumidor puede
 * comprobar sin interpretar textos que el documento proviene del adaptador simulado y no tiene valor
 * tributario.
 *
 * @param id                    UUID publico de la factura
 * @param invoiceNumber         correlativo dentro del punto de venta
 * @param taxDocumentType       tipo de documento fiscal emitido
 * @param emissionType          forma de emision
 * @param status                estado; {@code DEMO_ACCEPTED} en el entorno local
 * @param demo                  true cuando el documento no tiene validez fiscal
 * @param caption               leyenda impresa; en el MVP la leyenda no tributaria
 * @param cuf                   codigo unico del documento
 * @param taxReceiptCode        codigo de recepcion devuelto por el adaptador fiscal
 * @param qrUrl                 direccion del QR de verificacion; nula sin emision real
 * @param branchId              sucursal de la atencion
 * @param pointOfSale           caja emisora
 * @param customer              cliente congelado en la factura
 * @param totalAmount           total del documento; coincide con la suma de los subtotales
 * @param vatTaxableAmount      importe sujeto a IVA
 * @param totalDiscountAmount   suma de los descuentos del detalle
 * @param giftCardAmount        importe pagado con gift card; 0 en el MVP
 * @param currencyTotalAmount   total en la moneda del documento
 * @param exchangeRate          tipo de cambio aplicado
 * @param paymentMethodCode     metodo de pago del catalogo del SIN
 * @param issuedAt              instante de emision en ISO-8601 con zona
 * @param validatedAt           instante de la respuesta del adaptador fiscal
 * @param items                 detalle de la factura
 */
public record InvoiceResponse(
        UUID id,
        Long invoiceNumber,
        TaxDocumentType taxDocumentType,
        EmissionType emissionType,
        InvoiceStatus status,
        boolean demo,
        String caption,
        String cuf,
        String taxReceiptCode,
        String qrUrl,
        UUID branchId,
        PointOfSaleRef pointOfSale,
        InvoiceCustomerResponse customer,
        BigDecimal totalAmount,
        BigDecimal vatTaxableAmount,
        BigDecimal totalDiscountAmount,
        BigDecimal giftCardAmount,
        BigDecimal currencyTotalAmount,
        BigDecimal exchangeRate,
        Integer paymentMethodCode,
        OffsetDateTime issuedAt,
        OffsetDateTime validatedAt,
        List<InvoiceItemResponse> items,
        Integer cancellationReasonCode,
        OffsetDateTime voidedAt,
        UUID voidedByUserId) {
}
