package com.medicalsys.pacientes.api;

import com.medicalsys.pacientes.api.dto.CreatePatientRequest;
import com.medicalsys.pacientes.api.dto.PatientResponse;
import com.medicalsys.pacientes.domain.DocumentType;
import com.medicalsys.pacientes.security.RequiresRole;
import com.medicalsys.pacientes.security.Role;
import com.medicalsys.pacientes.service.PatientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresRole({Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
        PatientResponse response = service.create(request);
        URI location = URI.create("/api/v1/patients/" + response.patientId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{patientId}")
    @RequiresRole({Role.RECEPTIONIST, Role.PHYSICIAN, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public PatientResponse getById(@PathVariable UUID patientId) {
        return service.getByPublicId(patientId);
    }

    @GetMapping
    @RequiresRole({Role.RECEPTIONIST, Role.PHYSICIAN, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public PatientResponse getByDocument(
            @RequestParam DocumentType documentType,
            @RequestParam @NotBlank String documentNumber,
            @RequestParam(defaultValue = "") String documentComplement) {
        return service.getByDocument(documentType, documentNumber, documentComplement);
    }
}
