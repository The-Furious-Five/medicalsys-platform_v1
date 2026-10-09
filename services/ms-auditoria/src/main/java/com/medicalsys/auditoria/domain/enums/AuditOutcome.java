package com.medicalsys.auditoria.domain.enums;

/**
 * Desenlace de la operacion asentada.
 *
 * <p>Una bitacora que solo guarda exitos no sirve para auditar: el intento denegado y el fallo son
 * justamente lo que se revisa despues de un incidente.
 */
public enum AuditOutcome {

    /** La operacion se completo. */
    SUCCESS,
    /** La operacion se rechazo por falta de permiso, de rol o de tenant. */
    DENIED,
    /** La operacion se intento y termino en error del servicio o de validacion. */
    FAILURE
}
