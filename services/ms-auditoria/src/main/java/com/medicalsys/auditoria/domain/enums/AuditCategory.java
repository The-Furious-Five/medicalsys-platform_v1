package com.medicalsys.auditoria.domain.enums;

/**
 * Area funcional a la que pertenece el evento asentado.
 *
 * <p>La categoria es la unica dimension que un auditor puede recorrer sin conocer de antemano el
 * catalogo de acciones: {@code action} crece con cada historia, la categoria no. Por eso el filtro
 * principal de la pantalla de auditoria y los cortes del resumen se apoyan aqui.
 */
public enum AuditCategory {

    /** Inicio y cierre de sesion, refresco y fallos de credenciales (ms-identidad). */
    AUTHENTICATION,
    /** Denegaciones de rol o de organizacion detectadas por un servicio. */
    AUTHORIZATION,
    /** Alta, modificacion, busqueda y fusion del registro administrativo del paciente. */
    PATIENT_DATA,
    /** Acceso o escritura sobre expediente, encuentro, diagnostico y receta. */
    CLINICAL_RECORD,
    /** Carga, descarga y borrado logico de documentos clinicos. */
    DOCUMENT,
    /** Citas, disponibilidad y bloqueos de agenda. */
    SCHEDULING,
    /** Emision y anulacion de comprobantes. */
    BILLING,
    /** Cambios de organizacion, sucursal, plan, catalogos y claves de cifrado. */
    CONFIGURATION,
    /** Senal de seguridad: exportacion masiva, acceso fuera de horario, uso anomalo. */
    SECURITY,
    /** Actividad propia de la plataforma que no la origina una persona. */
    SYSTEM
}
