package com.medicalsys.profesionales.common.tenant;

import java.util.UUID;

/**
 * Identidad efectiva de la solicitud, derivada de claims confiables del JWT por el gateway.
 *
 * @param organizationId UUID publico del tenant; delimita toda consulta de este servicio
 * @param userId         usuario que ejecuta la accion, usado para trazabilidad
 */
public record RequestContext(UUID organizationId, UUID userId) {
}
