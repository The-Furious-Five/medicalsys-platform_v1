package com.medicalsys.identidad.domain;

/**
 * Codigos de los roles de sistema que siembra V2. No es la lista de roles validos: desde RBAC-02
 * se crean otros por la API y {@link Role#getCode()} es un texto libre con formato controlado.
 */
public enum RoleCode {
    PLATFORM_ADMIN,
    ADMINISTRATOR,
    PHYSICIAN,
    RECEPTIONIST,
    PATIENT,
    BILLING_CLERK
}
