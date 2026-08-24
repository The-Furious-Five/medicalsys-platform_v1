package com.medicalsys.facturacion.client;

import java.util.UUID;

/**
 * Puerto hacia ms-pacientes.
 *
 * <p>ms-facturacion no guarda pacientes: pide el DTO fiscal minimo por contrato HTTP y congela esos
 * datos dentro de la factura. Un paciente inexistente detiene la emision antes de tocar la base.
 */
public interface PatientsClient {

    /**
     * Devuelve el DTO fiscal minimo del paciente.
     *
     * @param organizationId tenant que factura; el propietario decide si el paciente es visible
     * @param patientId      paciente a facturar
     * @throws com.medicalsys.facturacion.common.error.BusinessRuleException  si el paciente no existe o no es facturable
     * @throws com.medicalsys.facturacion.common.error.DependencyUnavailableException si ms-pacientes no responde
     */
    FiscalCustomer fiscalCustomer(UUID organizationId, UUID patientId);
}
