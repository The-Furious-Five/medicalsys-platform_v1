package com.medicalsys.facturacion.domain.enums;

/**
 * Tipo de documento fiscal emitido ({@code invoice.tax_document_type}).
 *
 * <p>Tambien identifica el contador de {@code document_sequence}: cada tipo lleva su propio
 * correlativo dentro del punto de venta.
 */
public enum TaxDocumentType {
    /** Factura de compra-venta. Tipo por defecto del MVP. */
    PURCHASE_SALE_INVOICE,
    HEALTH_SECTOR_INVOICE,
    PROFESSIONAL_FEES_RECEIPT
}
