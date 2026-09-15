package com.medicalsys.catalogos.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxCatalogRepository extends JpaRepository<TaxCatalog, Long> {
    List<TaxCatalog> findByCatalogTypeAndActiveTrueOrderByDescriptionAsc(TaxCatalog.CatalogType catalogType);
}
