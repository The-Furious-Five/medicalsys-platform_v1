package com.medicalsys.pacientes.web.controller;

import com.medicalsys.pacientes.common.security.RequiresPermission;
import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.service.PatientService;
import com.medicalsys.pacientes.web.dto.PageResponse;
import com.medicalsys.pacientes.web.dto.PatientResponse;
import com.medicalsys.pacientes.web.dto.RegisterPatientRequest;
import com.medicalsys.pacientes.web.dto.UpdatePatientRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API pública del registro administrativo de pacientes. */
@Validated
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patients;

    public PatientController(PatientService patients) {
        this.patients = patients;
    }

    @PostMapping
    @RequiresPermission("patients.patient.create")
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody RegisterPatientRequest request) {
        PatientResponse response = patients.register(request);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + response.patientId())).body(response);
    }

    @GetMapping("/{patientId}")
    @RequiresPermission("patients.patient.read")
    public PatientResponse get(@PathVariable UUID patientId) {
        return patients.get(patientId);
    }

    /** Búsqueda exacta por la terna que también utiliza la restricción UNIQUE. */
    @GetMapping
    @RequiresPermission("patients.patient.read")
    public PatientResponse getByDocument(
            @RequestParam DocumentType documentType,
            @RequestParam @NotBlank @Size(max = 20) String documentNumber,
            @RequestParam(defaultValue = "") @Size(max = 5) String documentComplement) {
        return patients.getByDocument(documentType, documentNumber, documentComplement);
    }

    /** Búsqueda parcial separada para no cambiar el contrato de GET /patients. */
    @GetMapping("/search")
    @RequiresPermission("patients.patient.read")
    public PageResponse<PatientResponse> search(
            @RequestParam(required = false) @Size(max = 160) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(patients.search(q, pageable));
    }

    @PutMapping("/{patientId}")
    @RequiresPermission("patients.patient.update")
    public PatientResponse update(@PathVariable UUID patientId,
            @Valid @RequestBody UpdatePatientRequest request) {
        return patients.update(patientId, request);
    }
}
