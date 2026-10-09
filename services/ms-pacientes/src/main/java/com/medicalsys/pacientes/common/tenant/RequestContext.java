package com.medicalsys.pacientes.common.tenant;

import com.medicalsys.pacientes.common.security.Role;
import java.util.Set;
import java.util.UUID;

/**
 * Identidad efectiva de la solicitud, derivada de claims confiables del JWT por el gateway.
 *
 * @param organizationId UUID publico del tenant; delimita toda consulta de este servicio
 * @param userId         usuario que ejecuta la accion, usado para trazabilidad
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
