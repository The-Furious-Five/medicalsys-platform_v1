package com.medicalsys.catalogos.icd10;

import com.medicalsys.catalogos.common.security.InternalEndpoint;
import com.medicalsys.catalogos.common.security.RequiresPermission;
import com.medicalsys.catalogos.common.tenant.RequestContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints CIE-10.
 * GET /api/v1/catalogs/cie10?q=lumbago   → búsqueda (RF-11.1: exige personal autenticado)
 * GET /api/v1/catalogs/cie10/{code}       → detalle (RF-11.1: exige personal autenticado)
 * GET /internal/v1/icd10/{code}           → uso interno, sin identidad de usuario
 */
@RestController
public class Icd10Controller {

    private final Icd10Service service;

    public Icd10Controller(Icd10Service service) {
        this.service = service;
    }

    @GetMapping("/api/v1/catalogs/cie10")
    @RequiresPermission("catalogs.icd10.read")
    public ResponseEntity<List<Icd10Dto>> search(
            @RequestParam(required = false) String q) {
        RequestContextHolder.require();
        return ResponseEntity.ok(service.search(q));
    }

    @GetMapping("/api/v1/catalogs/cie10/{code}")
    @RequiresPermission("catalogs.icd10.read")
    public ResponseEntity<Icd10Dto> findByCode(@PathVariable String code) {
        RequestContextHolder.require();
        return ResponseEntity.ok(service.findByCode(code));
    }

    /** Contrato congelado con ms-historial: GET /internal/v1/cie10/{code}. */
    @GetMapping({"/internal/v1/cie10/{code}", "/internal/v1/icd10/{code}"})
    @InternalEndpoint
    public ResponseEntity<Icd10Dto> findByCodeInternal(@PathVariable String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }
}
