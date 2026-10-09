package com.medicalsys.facturacion.client;

import java.util.UUID;

/**
 * Puerto hacia ms-tenencia.
 *
 * <p>El precio de una prestacion nunca llega desde el cliente HTTP: lo publica su propietario. Asi,
 * quien emite la factura no puede elegir cuanto cobra.
 */
public interface ServiceCatalogClient {

    /**
     * Devuelve la prestacion facturable de la organizacion.
     *
     * @param organizationId    tenant propietario del catalogo
     * @param serviceOfferingId prestacion solicitada
     * @throws com.medicalsys.facturacion.common.error.BusinessRuleException  si no existe o no es facturable
     * @throws com.medicalsys.facturacion.common.error.DependencyUnavailableException si ms-tenencia no responde
     */
    BillableService billableService(UUID organizationId, UUID serviceOfferingId);
}
