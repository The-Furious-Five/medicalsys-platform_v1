package com.medicalsys.catalogos.common.tenant;

import java.util.Set;
import java.util.UUID;

/**
 * Identidad efectiva de la solicitud, derivada de claims confiables del JWT que el gateway
 * decodifica y repropaga como headers (X-User-Id, X-Organization-Id, X-Roles, X-Permissions).
 *
 * <p>Los catálogos son compartidos entre organizaciones (no se filtran por tenant), pero
 * RF-11.1 exige que solo personal autenticado pueda consultarlos. Esta clase es la evidencia
 * de esa autenticación: si no existe, la solicitud nunca pasó por un login válido.
 *
 * @param userId  usuario autenticado que ejecuta la consulta
 * @param organizationId tenant del usuario (informativo; los catálogos no lo usan para filtrar)
 * @param roles   roles vigentes del usuario
 * @param permissions permisos del token; deciden a que catalogo accede (RBAC-04)
 */
public record RequestContext(UUID userId, UUID organizationId, Set<String> roles, Set<String> permissions) {

    public RequestContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** Cierto si el token trae el permiso del catalogo que exige el endpoint (RBAC-04). */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
