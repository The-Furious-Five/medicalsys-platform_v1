package com.medicalsys.historial.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Convierte el UUID publico de Java al tipo fisico BINARY(16) definido en el diccionario.
 *
 * <p>Se registra como converter global para que toda referencia externa (UUID sin FK) use la misma
 * representacion binaria de 16 bytes, big-endian, sin guiones ni texto.
 */
@Converter(autoApply = true)
public class UuidBinaryConverter implements AttributeConverter<UUID, byte[]> {

    private static final int UUID_BYTES = 16;

    @Override
    public byte[] convertToDatabaseColumn(UUID attribute) {
        if (attribute == null) {
            return null;
        }
        return ByteBuffer.allocate(UUID_BYTES)
                .putLong(attribute.getMostSignificantBits())
                .putLong(attribute.getLeastSignificantBits())
                .array();
    }

    @Override
    public UUID convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        if (dbData.length != UUID_BYTES) {
            throw new IllegalStateException("Se esperaba un BINARY(16) y se leyeron " + dbData.length + " bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(dbData);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
