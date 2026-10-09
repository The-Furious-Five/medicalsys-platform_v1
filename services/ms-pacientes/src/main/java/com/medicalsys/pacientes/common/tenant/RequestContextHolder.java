package com.medicalsys.pacientes.common.tenant;

import com.medicalsys.pacientes.common.error.UnauthenticatedException;
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
     * <p>La usa el envio de auditoria, que necesita saber si hay actor pero no puede exigirlo: un
     * hecho sin identidad resuelta se asienta igual, con el actor vacio, en lugar de perderse.
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
