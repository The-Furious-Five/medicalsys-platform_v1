package com.medicalsys.catalogos.medication;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vademecum de demostracion.
 *
 * <p>La ruta interna en espanol es el contrato congelado con ms-historial.
 * La ruta inglesa queda como alias para no romper clientes existentes.
 */
@RestController
public class MedicationController {

    private final MedicationService service;

    public MedicationController(MedicationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/catalogs/medications")
    public ResponseEntity<List<MedicationDto>> listActive() {
        return ResponseEntity.ok(service.findAllActive());
    }

    @GetMapping("/api/v1/catalogs/medications/{code}")
    public ResponseEntity<MedicationDto> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }

    @GetMapping({"/internal/v1/medicamentos/{code}", "/internal/v1/medications/{code}"})
    public ResponseEntity<MedicationDto> findByCodeInternal(@PathVariable String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }
}
