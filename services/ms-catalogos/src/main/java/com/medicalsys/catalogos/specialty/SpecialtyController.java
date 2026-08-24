package com.medicalsys.catalogos.specialty;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de especialidades.
 * Público:   GET /api/v1/catalogs/specialties
 * Interno:   GET /internal/v1/specialties/{code}
 * Ping demo: GET /api/v1/catalogs/ping
 */
@RestController
public class SpecialtyController {

    private final SpecialtyService service;

    public SpecialtyController(SpecialtyService service) {
        this.service = service;
    }

    /** Lista todas las especialidades activas. */
    @GetMapping("/api/v1/catalogs/specialties")
    public ResponseEntity<List<SpecialtyDto>> listActive() {
        return ResponseEntity.ok(service.findAllActive());
    }

    /** Detalle interno para consumo de otros microservicios. */
    @GetMapping("/internal/v1/specialties/{code}")
    public ResponseEntity<SpecialtyDto> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }
}
