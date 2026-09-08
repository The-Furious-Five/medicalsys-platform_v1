package com.medicalsys.marketing.common.error;

/**
 * Dependencia sincrona temporalmente no disponible (HTTP 503).
 *
 * <p>Se prefiere un error claro sobre un reintento silencioso: el MVP conserva el correlationId y
 * deja el endurecimiento de reintentos para el backlog posterior.
 */
public class DependencyUnavailableException extends ApiException {

    public DependencyUnavailableException(String message, Throwable cause) {
        super(ErrorCode.DEPENDENCY_UNAVAILABLE, message, cause);
    }
}
