package com.medicalsys.agenda.config;

import java.util.Set;
import java.util.UUID;

/**
 * Identidad efectiva de la solicitud, derivada de claims confiables del JWT por el gateway.
 *
 * @param organizationId UUID publico del tenant; delimita toda consulta de este servicio
 * @param userId         usuario que ejecuta la accion, usado para trazabilidad
 * @param permissions    permisos del token (X-Permissions); deciden el acceso a cada endpoint
 */
public record RequestContext(UUID organizationId, UUID userId, Set<String> permissions) {

    public RequestContext {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** Cierto si el token trae el permiso del catalogo que exige el endpoint (RBAC-04). */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
