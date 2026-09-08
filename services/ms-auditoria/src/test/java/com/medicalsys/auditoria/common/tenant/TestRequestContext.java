package com.medicalsys.auditoria.common.tenant;

import com.medicalsys.auditoria.common.security.Role;
import java.util.Set;
import java.util.UUID;

/**
 * Publica una identidad en {@link RequestContextHolder} desde una prueba unitaria.
 *
 * <p>Vive en el paquete del contexto porque {@code set} y {@code clear} son de acceso restringido a
 * proposito: fuera de las pruebas, la unica forma de poblar el contexto tiene que ser el filtro que
 * lee los headers firmados por el gateway.
 */
public final class TestRequestContext {

    private TestRequestContext() {
    }

    public static void as(UUID organizationId, UUID userId, Role... roles) {
        RequestContextHolder.set(new RequestContext(organizationId, userId, Set.of(roles)));
    }

    public static void clear() {
        RequestContextHolder.clear();
    }
}
