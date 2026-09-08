package com.medicalsys.auditoria.common.tenant;

import com.medicalsys.auditoria.common.error.UnauthenticatedException;
import java.util.UUID;

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

    /**
     * Contexto de la solicitud, o null si el gateway no propago identidad.
     *
     * <p>La ingesta interna la usa porque un evento de autenticacion fallida llega antes de que
     * exista un tenant resuelto: en ese caso el productor declara la organizacion en el cuerpo.
     */
    public static RequestContext currentOrNull() {
        return CONTEXT.get();
    }

    /** Identidad humana obligatoria para escrituras que deben conservar autoría. */
    public static UUID requireUserId() {
        UUID userId = require().userId();
        if (userId == null) {
            throw new UnauthenticatedException("La solicitud no incluye X-User-Id valido");
        }
        return userId;
    }
}
