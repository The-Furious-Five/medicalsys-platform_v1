package com.medicalsys.catalogos.medication;

import com.medicalsys.catalogos.common.security.InternalEndpoint;
import com.medicalsys.catalogos.common.security.RequiresPermission;
import com.medicalsys.catalogos.common.tenant.RequestContextHolder;
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
 * Las rutas públicas exigen personal autenticado (RF-11.1); las internas no.
 */
@RestController
public class MedicationController {

    private final MedicationService service;

    public MedicationController(MedicationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/catalogs/medications")
    @RequiresPermission("catalogs.medication.read")
    public ResponseEntity<List<MedicationDto>> listActive() {
        RequestContextHolder.require();
        return ResponseEntity.ok(service.findAllActive());
    }

    @GetMapping("/api/v1/catalogs/medications/{code}")
    @RequiresPermission("catalogs.medication.read")
    public ResponseEntity<MedicationDto> findByCode(@PathVariable String code) {
        RequestContextHolder.require();
        return ResponseEntity.ok(service.findByCode(code));
    }

    @GetMapping({"/internal/v1/medicamentos/{code}", "/internal/v1/medications/{code}"})
    @InternalEndpoint
    public ResponseEntity<MedicationDto> findByCodeInternal(@PathVariable String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }
}
