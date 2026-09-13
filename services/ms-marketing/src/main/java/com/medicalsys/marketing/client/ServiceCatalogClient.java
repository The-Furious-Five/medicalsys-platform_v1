package com.medicalsys.marketing.client;

import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve una prestacion que marketing todavia no tiene replicada.
 *
 * <p>Hace falta en dos momentos: al atar un cupon a una prestacion concreta y al proyectar el
 * "ultimo servicio comprado" desde un evento de facturacion.
 */
public interface ServiceCatalogClient {

    /**
     * Devuelve la prestacion, o vacio si el propietario no la reconoce en ese tenant.
     *
     * @param organizationId    tenant que pregunta; viaja como header de contexto
     * @param serviceOfferingId prestacion consultada
     */
    Optional<ServiceOfferingRef> offering(UUID organizationId, UUID serviceOfferingId);
}
