package com.medicalsys.marketing.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/**
 * Convierte el UUID publico de Java al tipo fisico BINARY(16) definido en el diccionario.
 *
 * <p>Se registra como converter global para que toda referencia externa (UUID sin FK) use la misma
 * representacion binaria de 16 bytes, big-endian, sin guiones ni texto.
 */
@Converter(autoApply = true)
public class UuidBinaryConverter implements AttributeConverter<UUID, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(UUID attribute) {
        return Uuids.toBytes(attribute);
    }

    @Override
    public UUID convertToEntityAttribute(byte[] dbData) {
        return Uuids.fromBytes(dbData);
    }
}
