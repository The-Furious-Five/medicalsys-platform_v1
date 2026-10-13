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
 * @param permissions    permisos del catalogo que trae el token (X-Permissions)
 */
public record RequestContext(UUID organizationId, UUID userId, Set<Role> roles, Set<String> permissions) {

    public RequestContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** Contexto sin permisos: para procesos internos y pruebas que no pasan por la autorizacion. */
    public RequestContext(UUID organizationId, UUID userId, Set<Role> roles) {
        this(organizationId, userId, roles, Set.of());
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
