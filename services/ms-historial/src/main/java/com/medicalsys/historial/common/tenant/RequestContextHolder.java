package com.medicalsys.historial.common.tenant;

import com.medicalsys.historial.common.error.UnauthenticatedException;

/** Contexto de identidad asociado al hilo que atiende la solicitud. */
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
     * @throws UnauthenticatedException si la ruta exige identidad y el gateway no la propago
     */
    public static RequestContext require() {
        RequestContext context = CONTEXT.get();
        if (context == null) {
            throw new UnauthenticatedException("La solicitud no incluye una identidad valida propagada por el gateway");
        }
        return context;
    }
}
