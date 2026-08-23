package com.medicalsys.catalogos.icd10;

public record Icd10Dto(
        String code,
        String description,
        String chapter,
        String category,
        String applicableSex,
        boolean active
) {
    public static Icd10Dto from(Icd10Catalog e) {
        return new Icd10Dto(
                e.getCode(),
                e.getDescription(),
                e.getChapter(),
                e.getCategory(),
                e.getApplicableSex() != null ? e.getApplicableSex().name() : null,
                e.isActive()
        );
    }
}
