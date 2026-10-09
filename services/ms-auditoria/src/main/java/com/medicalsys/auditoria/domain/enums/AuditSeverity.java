package com.medicalsys.auditoria.domain.enums;

/**
 * Relevancia del evento para quien revisa la bitacora.
 *
 * <p>No es el nivel de log tecnico del productor: describe cuanto le importa el hecho a un auditor
 * clinico. Un 500 puede ser INFO si no toco datos de paciente; una lectura exitosa de expediente
 * ajeno es CRITICAL aunque el HTTP haya sido 200.
 */
public enum AuditSeverity {

    INFO,
    NOTICE,
    WARNING,
    CRITICAL
}
