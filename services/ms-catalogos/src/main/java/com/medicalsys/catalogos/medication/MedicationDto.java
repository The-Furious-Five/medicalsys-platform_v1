package com.medicalsys.catalogos.medication;

public record MedicationDto(
        Long id,
        String code,
        String genericName,
        String tradeName,
        String concentration,
        String pharmaceuticalForm,
        String administrationRoute,
        boolean controlled,
        boolean requiresPrescription,
        boolean active
) {
    public static MedicationDto from(Medication e) {
        return new MedicationDto(
                e.getId(),
                e.getCode(),
                e.getGenericName(),
                e.getTradeName(),
                e.getConcentration(),
                e.getPharmaceuticalForm() != null ? e.getPharmaceuticalForm().name() : null,
                e.getAdministrationRoute() != null ? e.getAdministrationRoute().name() : null,
                e.isControlled(),
                e.isRequiresPrescription(),
                e.isActive()
        );
    }
}
