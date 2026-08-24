package com.medicalsys.catalogos.studytype;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de tipos de estudio.
 * GET /api/v1/catalogs/study-types          → lista activos
 * GET /internal/v1/study-types/{code}       → uso interno (ms-historial)
 */
@RestController
public class StudyTypeController {

    private final StudyTypeService service;

    public StudyTypeController(StudyTypeService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/catalogs/study-types")
    public ResponseEntity<List<StudyTypeDto>> listActive() {
        return ResponseEntity.ok(service.findAllActive());
    }

    /** Ruta interna: ms-historial consulta el tipo antes de crear una orden. */
    @GetMapping("/internal/v1/study-types/{code}")
    public ResponseEntity<StudyTypeDto> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }
}
