package com.medicalsys.tenencia.common.error;

/** Conflicto de estado o de unicidad (HTTP 409). */
public class ConflictException extends ApiException {

    public ConflictException(ErrorCode code, String message) {
        super(code, message);
    }
}
