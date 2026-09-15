package com.medicalsys.catalogos.common.tenant;

import java.util.Set;
import java.util.UUID;

/**
 * Identidad efectiva de la solicitud, derivada de claims confiables del JWT que el gateway
 * decodifica y repropaga como headers (X-User-Id, X-Organization-Id, X-Roles).
 *
 * <p>Los catálogos son compartidos entre organizaciones (no se filtran por tenant), pero
 * RF-11.1 exige que solo personal autenticado pueda consultarlos. Esta clase es la evidencia
 * de esa autenticación: si no existe, la solicitud nunca pasó por un login válido.
 *
 * @param userId  usuario autenticado que ejecuta la consulta
 * @param organizationId tenant del usuario (informativo; los catálogos no lo usan para filtrar)
 * @param roles   roles vigentes del usuario
 */
public record RequestContext(UUID userId, UUID organizationId, Set<String> roles) {

    public RequestContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
