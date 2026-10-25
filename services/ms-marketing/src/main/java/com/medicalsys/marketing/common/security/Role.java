package com.medicalsys.marketing.common.security;

import java.util.Locale;
import java.util.Optional;

/**
 * Roles de la plataforma segun {@code role.code} de identity_db.
 *
 * <p>ms-marketing no crea roles: solo verifica los que el gateway propaga desde claims confiables.
 *
 * <p>Los permisos del JWT autorizan las operaciones. Los codigos de rol preservan la trazabilidad
 * de las quince funciones de la matriz aprobada.
 */
public enum Role {
    PLATFORM_ADMIN,
    OSI,
    AUDITOR,
    EXTERNAL_AUDITOR,
    ADMINISTRATOR,
    PHYSICIAN,
    MEDICAL_INTERN,
    NURSE,
    LAB_TECHNICIAN,
    RECEPTIONIST,
    BILLING_CLERK,
    BILLING_SUPERVISOR,
    MARKETING_MANAGER,
    PATIENT,
    DEVOPS;

    /** Traduce un codigo recibido en el header; un rol desconocido se ignora en vez de fallar. */
    public static Optional<Role> parse(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Role.valueOf(code.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
