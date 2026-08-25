package com.medicalsys.tenencia.web.dto;

import java.util.UUID;

/**
 * Proyeccion minima de una sede para consumo interno.
 *
 * <p>ms-agenda la usa para ubicar una cita y ms-facturacion para anclar un punto de venta. Ninguno
 * necesita coordenadas ni horarios, asi que no los recibe.
 */
public record BranchRef(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        boolean headquarters,
        String city,
        boolean active) {
}
