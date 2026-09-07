package com.medicalsys.auditoria.common.error;

/**
 * Recurso inexistente o no visible para el tenant.
 *
 * <p>Un recurso de otra organizacion se responde como 404 y no como 403: revelar su existencia
 * romperia el aislamiento multi-tenant.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(ErrorCode code, String message) {
        super(code, message);
    }
}
