package com.medicalsys.catalogos.specialty;

import com.medicalsys.catalogos.common.security.InternalEndpoint;
import com.medicalsys.catalogos.common.security.RequiresPermission;
import com.medicalsys.catalogos.common.tenant.RequestContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de especialidades.
 * Público:   GET /api/v1/catalogs/specialties (RF-11.1: exige personal autenticado)
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
    @RequiresPermission("catalogs.specialty.read")
    public ResponseEntity<List<SpecialtyDto>> listActive() {
        RequestContextHolder.require();
        return ResponseEntity.ok(service.findAllActive());
    }

    /** Detalle interno para consumo de otros microservicios. */
    @GetMapping("/internal/v1/specialties/{code}")
    @InternalEndpoint
    public ResponseEntity<SpecialtyDto> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }
}
