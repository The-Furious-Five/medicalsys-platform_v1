package com.medicalsys.tenencia.common.error;

/** Identidad valida sin permiso para la operacion (HTTP 403). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
