package com.medicalsys.pacientes.web.dto;

import java.util.UUID;

/**
 * Cliente fiscal del paciente, para ms-facturacion.
 *
 * <p>Es el contrato que {@code com.medicalsys.facturacion.client.FiscalCustomer} espera, campo por
 * campo. Se separa de {@link BillingPatientSummary} porque no son la misma cosa: aquel expone
 * {@code fullName} y {@code status}, y facturacion necesita {@code legalName} -el nombre que se
 * imprime en el documento- y el correo al que enviarlo. Con el DTO anterior, {@code legalName}
 * llegaba nulo y la emision fallaba con NullPointerException dentro del record.
 *
 * <p>Deliberadamente pobre: identificador, documento, nombre y correo. Facturacion no recibe
 * telefono, direccion ni ningun dato clinico.
 *
 * @param patientId          UUID publico del paciente
 * @param documentType       tipo de documento en el vocabulario de la plataforma
 * @param documentNumber     numero de documento
 * @param documentComplement complemento del CI boliviano; nulo si no tiene
 * @param legalName          nombre que se imprime en la factura
 * @param email              correo para enviar el documento; opcional
 */
public record FiscalCustomerResponse(
        UUID patientId,
        String documentType,
        String documentNumber,
        String documentComplement,
        String legalName,
        String email) {
}
