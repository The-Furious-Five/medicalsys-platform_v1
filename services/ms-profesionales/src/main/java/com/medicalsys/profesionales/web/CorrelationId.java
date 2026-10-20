package com.medicalsys.profesionales.web;

import java.util.UUID;
import org.slf4j.MDC;

/** Acceso al identificador de correlacion de la solicitud en curso. */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    /** Devuelve el correlationId vigente o uno nuevo si la solicitud no llego por HTTP. */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value != null ? value : UUID.randomUUID().toString();
    }
}
