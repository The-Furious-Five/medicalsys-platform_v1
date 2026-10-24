package com.medicalsys.auditoria.service;

import com.medicalsys.auditoria.common.error.ForbiddenException;
import com.medicalsys.auditoria.common.tenant.RequestContext;
import com.medicalsys.auditoria.common.tenant.RequestContextHolder;
import java.util.UUID;

/**
 * Organizacion cuya bitacora puede leer la solicitud en curso.
 *
 * <p>Un auditor ve solo la de su organizacion. El OSI custodia los accesos de toda la plataforma
 * (ISO-01): con {@code audit.log.read_platform} ve todas, o la que elija. Pedir otra organizacion sin
 * ese permiso es un 403, no un filtro que se ignora en silencio.
 */
public final class AuditScope {

    public static final String PLATFORM_PERMISSION = "audit.log.read_platform";

    private AuditScope() {
    }

    /** Organizacion a consultar; null significa todas (solo con alcance de plataforma). */
    public static UUID resolve(UUID requested) {
        RequestContext context = RequestContextHolder.require();
        if (context.hasPermission(PLATFORM_PERMISSION)) {
            return requested;
        }
        if (requested != null && !requested.equals(context.organizationId())) {
            throw new ForbiddenException("Solo el alcance de plataforma consulta la bitacora de otra organizacion");
        }
        return context.organizationId();
    }

    /** Igual que {@link #resolve}, pero siempre una: la cadena de hashes es por organizacion. */
    public static UUID resolveSingle(UUID requested) {
        UUID organizationId = resolve(requested);
        return organizationId != null ? organizationId : RequestContextHolder.require().organizationId();
    }
}
