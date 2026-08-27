package com.medicalsys.pacientes.security;

import java.util.Locale;
import java.util.Optional;

public enum Role {
    PLATFORM_ADMIN,
    ADMINISTRATOR,
    PHYSICIAN,
    RECEPTIONIST,
    PATIENT,
    BILLING_CLERK;

    public static Optional<Role> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Role.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
