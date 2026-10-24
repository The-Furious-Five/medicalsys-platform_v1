package com.medicalsys.identidad.domain;

/**
 * Codigos de los roles de sistema que siembra V10 (matriz aprobada de 15 roles). No es la lista de roles validos: desde RBAC-02
 * se crean otros por la API y {@link Role#getCode()} es un texto libre con formato controlado.
 */
public enum RoleCode {
    PLATFORM_ADMIN,
    ADMINISTRATOR,
    PHYSICIAN,
    RECEPTIONIST,
    PATIENT,
    BILLING_CLERK,
    // ISO-01
    OSI,
    AUDITOR,
    NURSE,
    LAB_TECHNICIAN,
    MARKETING_MANAGER,
    EXTERNAL_AUDITOR,
    MEDICAL_INTERN,
    BILLING_SUPERVISOR,
    DEVOPS
}
