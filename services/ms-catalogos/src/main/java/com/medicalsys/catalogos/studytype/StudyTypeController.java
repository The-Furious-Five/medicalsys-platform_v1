package com.medicalsys.catalogos.studytype;

import com.medicalsys.catalogos.common.security.InternalEndpoint;
import com.medicalsys.catalogos.common.security.RequiresPermission;
import com.medicalsys.catalogos.common.tenant.RequestContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de tipos de estudio.
 * GET /api/v1/catalogs/study-types          → lista activos (RF-11.1: exige personal autenticado)
 * GET /internal/v1/study-types/{code}       → uso interno (ms-historial), sin identidad de usuario
 */
@RestController
public class StudyTypeController {

    private final StudyTypeService service;

    public StudyTypeController(StudyTypeService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/catalogs/study-types")
    @RequiresPermission("catalogs.study_type.read")
    public ResponseEntity<List<StudyTypeDto>> listActive() {
        RequestContextHolder.require();
        return ResponseEntity.ok(service.findAllActive());
    }

    /** Contrato congelado con ms-historial: GET /internal/v1/tipos-estudio/{code}. */
    @GetMapping({"/internal/v1/tipos-estudio/{code}", "/internal/v1/study-types/{code}"})
    @InternalEndpoint
    public ResponseEntity<StudyTypeDto> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }
}
