package com.medicalsys.catalogos.tax;

import com.medicalsys.catalogos.common.tenant.RequestContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Consulta pública de las opciones tributarias activas de la réplica del SIN. */
@RestController
@RequestMapping("/api/v1/catalogs/tax")
public class TaxCatalogController {

    private final TaxCatalogService service;

    public TaxCatalogController(TaxCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaxCatalogDto> list(@RequestParam TaxCatalog.CatalogType type) {
        RequestContextHolder.require();
        return service.findActive(type);
    }
}
