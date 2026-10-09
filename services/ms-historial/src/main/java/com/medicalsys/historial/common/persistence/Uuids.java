package com.medicalsys.historial.common.persistence;

import java.util.UUID;

/** Utilidades de UUID: los identificadores viajan como texto canonico de 36 caracteres. */
public final class Uuids {

    private Uuids() {
    }

    /** Genera el identificador publico de una entidad nueva. */
    public static UUID newPublicId() {
        return UUID.randomUUID();
    }

    /**
     * Convierte texto a UUID devolviendo {@code null} cuando la entrada esta vacia.
     *
     * @throws IllegalArgumentException si el texto no es un UUID canonico
     */
    public static UUID parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value.trim());
    }
}
