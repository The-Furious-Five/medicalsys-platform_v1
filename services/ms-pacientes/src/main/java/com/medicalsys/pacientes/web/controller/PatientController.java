package com.medicalsys.pacientes.web.controller;

import com.medicalsys.pacientes.common.security.RequiresRole;
import com.medicalsys.pacientes.common.security.Role;
import com.medicalsys.pacientes.common.tenant.RequestContextHolder;
import com.medicalsys.pacientes.service.PatientService;
import com.medicalsys.pacientes.web.dto.PageResponse;
import com.medicalsys.pacientes.web.dto.PatientResponse;
import com.medicalsys.pacientes.web.dto.RegisterPatientRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pacientes de la clinica.
 *
 * <p>Registrar no es idempotente, pero si es seguro reintentarlo: un segundo intento con el mismo
 * documento devuelve 409 en vez de crear un duplicado.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    /** Registra un paciente; un documento repetido devuelve 409. */
    @PostMapping
    @RequiresRole({Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PHYSICIAN, Role.PLATFORM_ADMIN})
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody RegisterPatientRequest request) {
        RequestContextHolder.require();
        PatientResponse patient = service.register(request);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + patient.patientId())).body(patient);
    }

    /** Ficha completa de un paciente. */
    @GetMapping("/{patientId}")
    @RequiresRole({Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PHYSICIAN, Role.BILLING_CLERK, Role.PLATFORM_ADMIN})
    public PatientResponse get(@PathVariable UUID patientId) {
        RequestContextHolder.require();
        return service.get(patientId);
    }

    /** Busqueda por nombre o documento para la pantalla de recepcion. */
    @GetMapping
    @RequiresRole({Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PHYSICIAN, Role.BILLING_CLERK, Role.PLATFORM_ADMIN})
    public PageResponse<PatientResponse> search(@RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        RequestContextHolder.require();
        return PageResponse.of(service.search(q, pageable));
    }
}
