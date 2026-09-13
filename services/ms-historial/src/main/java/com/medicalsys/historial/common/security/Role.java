package com.medicalsys.historial.common.security;

import java.util.Locale;
import java.util.Optional;

/**
 * Roles de la plataforma segun {@code role.code} de identity_db.
 *
 * <p>ms-historial no crea roles: solo verifica los que el gateway propaga desde claims confiables.
 */
public enum Role {
    PLATFORM_ADMIN,
    ADMINISTRATOR,
    PHYSICIAN,
    RECEPTIONIST,
    PATIENT,
    BILLING_CLERK;

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
