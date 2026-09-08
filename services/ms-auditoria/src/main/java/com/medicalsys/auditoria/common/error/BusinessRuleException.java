package com.medicalsys.auditoria.common.error;

/** Regla de negocio no procesable con la entidad recibida (HTTP 422). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(ErrorCode code, String message) {
        super(code, message);
    }
}
