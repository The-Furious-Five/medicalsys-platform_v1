package com.medicalsys.facturacion.client;

import com.medicalsys.facturacion.domain.enums.InvoiceStatus;
import com.medicalsys.facturacion.domain.enums.TaxDocumentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto hacia el sistema fiscal.
 *
 * <p>Existe para que el dominio no sepa si detras hay un stub o un servicio real: el flujo de
 * emision llama siempre a este puerto y guarda lo que devuelve. El unico adaptador de este sprint es
 * {@link SimulatedTaxAuthorityGateway}, determinista y sin red.
 *
 * <p>Cuando exista integracion real, se agrega otra implementacion condicionada por
 * {@code medicalsys.tax-authority.mode} y no cambia nada en el servicio de facturas.
 */
public interface TaxAuthorityGateway {

    /** Identidad fiscal de un punto de venta, tal como la exige cualquier solicitud al sistema. */
    record PointOfSaleIdentity(String nit, int taxBranchCode, int pointOfSaleCode) {
    }

    /**
     * Codigo de inicio de sistemas concedido.
     *
     * @param code       codigo asignado
     * @param validUntil ultima fecha de vigencia
     */
    record CuisGrant(String code, LocalDate validUntil) {
    }

    /**
     * Codigo de facturacion diaria concedido.
     *
     * @param code        codigo asignado
     * @param controlCode codigo de control asociado
     * @param validFrom   inicio de la vigencia
     * @param validUntil  fin de la vigencia
     */
    record CufdGrant(String code, String controlCode, Instant validFrom, Instant validUntil) {
    }

    /**
     * Datos minimos de la factura que se envian al sistema fiscal.
     *
     * <p>No incluye ningun dato clinico: solo identidad fiscal, correlativo, cliente e importe.
     *
     * @param identity               identidad fiscal del punto de venta emisor
     * @param taxDocumentType        tipo de documento emitido
     * @param invoiceNumber          correlativo asignado
     * @param cufdCode               CUFD vigente en la emision
     * @param customerDocumentNumber documento del cliente
     * @param totalAmount            importe total del documento
     * @param issuedAt               instante de emision
     */
    record InvoiceSubmission(
            PointOfSaleIdentity identity,
            TaxDocumentType taxDocumentType,
            long invoiceNumber,
            String cufdCode,
            String customerDocumentNumber,
            BigDecimal totalAmount,
            Instant issuedAt) {
    }

    /**
     * Respuesta del sistema fiscal a una factura.
     *
     * @param cuf         codigo unico del documento
     * @param status      estado resultante
     * @param receiptCode codigo de recepcion, si lo hubo
     * @param caption     leyenda que debe imprimirse en el documento
     * @param qrUrl       direccion del QR de verificacion; nula si no hay emision real
     * @param validatedAt instante de la respuesta
     */
    record InvoiceReceipt(
            String cuf,
            InvoiceStatus status,
            String receiptCode,
            String caption,
            String qrUrl,
            Instant validatedAt) {
    }

    /** Solicita el CUIS del punto de venta. */
    CuisGrant requestCuis(PointOfSaleIdentity identity, Instant requestedAt);

    /** Solicita el CUFD vigente del punto de venta a partir del instante indicado. */
    CufdGrant requestCufd(PointOfSaleIdentity identity, String cuisCode, Instant validFrom);

    /** Envia la factura y devuelve el CUF y el estado resultante. */
    InvoiceReceipt submit(InvoiceSubmission submission);

    /** Indica si el adaptador activo es una simulacion y no una autoridad fiscal real. */
    boolean isSimulated();
}
