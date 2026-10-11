package com.medicalsys.catalogos.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 403 de autorizacion (RBAC-04). El codigo es FORBIDDEN, como el resto de errores del servicio
 * se identifica por su reason, y el detalle explica que permiso falta.
 */
public class PermissionDeniedException extends ResponseStatusException {

    public PermissionDeniedException(String detail) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN");
        setDetail(detail);
    }

    /** El motivo concreto, para el log, en lugar del codigo generico que lleva el reason. */
    @Override
    public String getMessage() {
        return getBody().getDetail();
    }
}
