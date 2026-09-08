package com.medicalsys.marketing.common.persistence;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Conversion entre {@link UUID} y el {@code BINARY(16)} que usa la plataforma.
 *
 * <p>Existe aparte de {@link UuidBinaryConverter} porque las consultas de audiencia se ejecutan con
 * SQL nativo -- el criterio es dinamico y armarlo con la API de criterios de JPA seria mas dificil
 * de leer que el propio SQL -- y ahi hace falta la misma representacion binaria sin pasar por el
 * conversor de JPA. Un solo lugar define los 16 bytes big-endian; el conversor delega aqui.
 */
public final class Uuids {

    /** Longitud fisica de la columna. */
    public static final int UUID_BYTES = 16;

    private Uuids() {
    }

    /** Serializa a 16 bytes big-endian; {@code null} se propaga. */
    public static byte[] toBytes(UUID value) {
        if (value == null) {
            return null;
        }
        return ByteBuffer.allocate(UUID_BYTES)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    /** Reconstruye el UUID; {@code null} se propaga. */
    public static UUID fromBytes(byte[] value) {
        if (value == null) {
            return null;
        }
        if (value.length != UUID_BYTES) {
            throw new IllegalStateException("Se esperaba un BINARY(16) y se leyeron " + value.length + " bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
