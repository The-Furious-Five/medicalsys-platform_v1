package com.medicalsys.catalogos.specialty;

/**
 * DTO de especialidad expuesto en el contrato HTTP.
 * No expone la entidad JPA directamente.
 */
public record SpecialtyDto(Integer id, String code, String name, boolean active) {

    public static SpecialtyDto from(Specialty s) {
        return new SpecialtyDto(s.getId(), s.getCode(), s.getName(), s.isActive());
    }
}
