package com.medicalsys.facturacion.web.dto;

import java.util.UUID;

/**
 * Cliente de la factura, congelado desde el DTO fiscal minimo de ms-pacientes.
 *
 * @param patientId                 paciente facturado
 * @param identityDocumentTypeCode  tipo de documento del catalogo del SIN; 1 = CI
 * @param documentNumber            numero de documento
 * @param documentComplement        complemento del CI, si lo tiene
 * @param legalName                 nombre o razon social impreso
 * @param email                     correo de envio, si se conoce
 */
public record InvoiceCustomerResponse(
        UUID patientId,
        Integer identityDocumentTypeCode,
        String documentNumber,
        String documentComplement,
        String legalName,
        String email) {
}
