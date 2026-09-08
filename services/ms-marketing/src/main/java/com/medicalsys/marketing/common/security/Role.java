package com.medicalsys.marketing.common.security;

import java.util.Locale;
import java.util.Optional;

/**
 * Roles de la plataforma segun {@code role.code} de identity_db.
 *
 * <p>ms-marketing no crea roles: solo verifica los que el gateway propaga desde claims confiables.
 *
 * <p>{@code MARKETING_MANAGER} esta declarado para el dia en que ms-identidad lo emita, pero hoy
 * ningun token lo trae. Por eso toda operacion de gestion acepta ademas {@code ADMINISTRATOR}: si
 * dependiera solo del rol futuro, el servicio quedaria inalcanzable en el entorno actual.
 */
public enum Role {
    PLATFORM_ADMIN,
    ADMINISTRATOR,
    MARKETING_MANAGER,
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
