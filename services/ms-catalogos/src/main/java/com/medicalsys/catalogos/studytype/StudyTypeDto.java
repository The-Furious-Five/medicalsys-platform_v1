package com.medicalsys.catalogos.studytype;

import java.math.BigDecimal;

public record StudyTypeDto(
        Integer id,
        String code,
        String name,
        String category,
        boolean requiresFasting,
        boolean generatesImage,
        String resultUnit,
        BigDecimal referenceMin,
        BigDecimal referenceMax,
        boolean active
) {
    public static StudyTypeDto from(StudyType e) {
        return new StudyTypeDto(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getCategory() != null ? e.getCategory().name() : null,
                e.isRequiresFasting(),
                e.isGeneratesImage(),
                e.getResultUnit(),
                e.getReferenceMin(),
                e.getReferenceMax(),
                e.isActive()
        );
    }
}
