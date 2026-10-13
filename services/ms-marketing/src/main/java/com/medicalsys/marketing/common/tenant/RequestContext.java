package com.medicalsys.marketing.common.tenant;

import com.medicalsys.marketing.common.security.Role;
import java.util.Set;
import java.util.UUID;

/**
 * Identidad efectiva de la solicitud, derivada de claims confiables del JWT por el gateway.
 *
 * @param organizationId tenant propietario de los datos; delimita toda consulta
 * @param userId         usuario que ejecuta la accion, usado para trazabilidad de anulaciones
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
