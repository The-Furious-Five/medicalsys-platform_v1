package com.medicalsys.profesionales.common.tenant;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
     * @throws ResponseStatusException 401 si la ruta exige identidad y el gateway no la propago
     */
    public static RequestContext require() {
        RequestContext context = CONTEXT.get();
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "La solicitud no incluye una identidad valida propagada por el gateway");
        }
        return context;
    }
}
