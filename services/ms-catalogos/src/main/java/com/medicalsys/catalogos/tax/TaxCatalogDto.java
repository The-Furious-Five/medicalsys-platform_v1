package com.medicalsys.catalogos.tax;

/** Opción tributaria que puede seleccionar el frontend. */
public record TaxCatalogDto(
        Long id,
        TaxCatalog.CatalogType catalogType,
        String code,
        String description,
        boolean active) {

    public static TaxCatalogDto from(TaxCatalog entry) {
        return new TaxCatalogDto(entry.getId(), entry.getCatalogType(), entry.getCode(),
                entry.getDescription(), entry.isActive());
    }
}
