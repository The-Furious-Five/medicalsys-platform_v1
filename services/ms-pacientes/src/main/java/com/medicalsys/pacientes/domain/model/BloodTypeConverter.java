package com.medicalsys.pacientes.domain.model;

import com.medicalsys.pacientes.domain.enums.BloodType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/** Convierte nombres Java legibles como {@code A_POSITIVE} al valor fisico {@code A+}. */
@Converter
public class BloodTypeConverter implements AttributeConverter<BloodType, String> {

    @Override
    public String convertToDatabaseColumn(BloodType attribute) {
        return attribute == null ? null : attribute.databaseValue();
    }

    @Override
    public BloodType convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(BloodType.values())
                .filter(type -> type.databaseValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Grupo sanguineo desconocido: " + value));
    }
}
