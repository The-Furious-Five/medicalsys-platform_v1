package com.medicalsys.catalogos.tax;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaxCatalogService {

    private final TaxCatalogRepository repository;

    public TaxCatalogService(TaxCatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TaxCatalogDto> findActive(TaxCatalog.CatalogType type) {
        return repository.findByCatalogTypeAndActiveTrueOrderByDescriptionAsc(type)
                .stream()
                .map(TaxCatalogDto::from)
                .toList();
    }
}
