package com.medicalsys.auditoria.common.error;

/** Identidad ausente, invalida o no propagada por el gateway (HTTP 401). */
public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }
}
