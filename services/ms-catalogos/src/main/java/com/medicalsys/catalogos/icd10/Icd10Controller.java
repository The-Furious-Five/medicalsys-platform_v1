package com.medicalsys.catalogos.icd10;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints CIE-10.
 * GET /api/v1/catalogs/cie10?q=lumbago   → búsqueda
 * GET /api/v1/catalogs/cie10/{code}       → detalle
 * GET /internal/v1/icd10/{code}           → uso interno
 */
@RestController
public class Icd10Controller {

    private final Icd10Service service;

    public Icd10Controller(Icd10Service service) {
        this.service = service;
    }

    @GetMapping("/api/v1/catalogs/cie10")
    public ResponseEntity<List<Icd10Dto>> search(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.search(q));
    }

    @GetMapping("/api/v1/catalogs/cie10/{code}")
    public ResponseEntity<Icd10Dto> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }

    /** Contrato congelado con ms-historial: GET /internal/v1/cie10/{code}. */
    @GetMapping({"/internal/v1/cie10/{code}", "/internal/v1/icd10/{code}"})
    public ResponseEntity<Icd10Dto> findByCodeInternal(@PathVariable String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }
}
