package com.medicalsys.pacientes.web.dto;

import java.util.UUID;

/**
 * DTO fiscal minimo consumido por ms-facturacion en la ruta interna.
 *
 * <p>Lleva solo lo que hace falta para emitir: documento y nombre. Ningun dato clinico ni de
 * contacto cruza esta frontera.
 */
public record FiscalCustomerResponse(
        UUID patientId,
        String documentType,
        String documentNumber,
        String documentComplement,
        String fullName,
        String email) {
}
