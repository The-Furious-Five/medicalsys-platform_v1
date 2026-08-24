package com.medicalsys.criptografia.key;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Convierte el enum Java al valor con guiones definido por el diccionario. */
@Converter
public class AlgorithmConverter implements AttributeConverter<EncryptionKey.Algorithm, String> {

    @Override
    public String convertToDatabaseColumn(EncryptionKey.Algorithm algorithm) {
        return algorithm == null ? null : algorithm.toDbValue();
    }

    @Override
    public EncryptionKey.Algorithm convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : EncryptionKey.Algorithm.fromDbValue(databaseValue);
    }
}
