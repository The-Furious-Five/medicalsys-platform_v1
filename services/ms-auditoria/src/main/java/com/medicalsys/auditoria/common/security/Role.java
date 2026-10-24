package com.medicalsys.auditoria.common.security;

import java.util.Locale;
import java.util.Optional;

/**
 * Roles de la plataforma segun {@code role.code} de identity_db.
 *
 * <p>ms-auditoria no crea roles: solo verifica los que el gateway propaga desde claims confiables.
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
