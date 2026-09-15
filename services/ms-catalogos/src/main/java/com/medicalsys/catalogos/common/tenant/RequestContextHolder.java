package com.medicalsys.catalogos.common.tenant;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Contexto de identidad asociado al hilo que atiende la solicitud.
 *
 * <p>{@link #require()} es el punto donde RF-11.1 se hace cumplir: las rutas públicas de
 * catálogos ({@code /api/v1/catalogs/**}) deben invocarlo antes de resolver la consulta. Las
 * rutas internas ({@code /internal/v1/**}) NO lo invocan: son tráfico servicio-a-servicio sin
 * JWT de usuario, protegido en cambio por aislamiento de red (RF-10.4).
 */
public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    private RequestContextHolder() {
    }

    static void set(RequestContext context) {
        CONTEXT.set(context);
    }

    static void clear() {
        CONTEXT.remove();
    }

    /**
     * Devuelve el contexto de la solicitud en curso.
     *
     * @throws ResponseStatusException 401 UNAUTHENTICATED si el gateway no propagó una identidad
     *                                  válida (sin JWT, o JWT inválido/revocado)
     */
    public static RequestContext require() {
        RequestContext context = CONTEXT.get();
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        }
        return context;
    }
}
