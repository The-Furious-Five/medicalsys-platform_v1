package com.medicalsys.facturacion.common.tenant;

import com.medicalsys.facturacion.common.security.Role;
import java.util.Set;
import java.util.UUID;

/**
 * Identidad efectiva de la solicitud, derivada de claims confiables del JWT por el gateway.
 *
 * @param organizationId tenant propietario de los datos; delimita toda consulta
 * @param userId         usuario que ejecuta la accion, usado para trazabilidad de anulaciones
 * @param roles          roles vigentes del usuario en la organizacion
 */
public record RequestContext(UUID organizationId, UUID userId, Set<Role> roles) {

    public RequestContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
