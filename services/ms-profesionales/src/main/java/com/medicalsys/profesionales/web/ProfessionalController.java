package com.medicalsys.profesionales.web;

import com.medicalsys.profesionales.service.ProfessionalService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProfessionalController {
    private final ProfessionalService service;
    public ProfessionalController(ProfessionalService service) { this.service = service; }

    @PostMapping("/api/v1/professionals")
    public ResponseEntity<ProfessionalResponse> create(@Valid @RequestBody CreateProfessionalRequest request) {
        ProfessionalResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/professionals/" + response.id())).body(response);
    }

    @GetMapping("/api/v1/professionals")
    public List<ProfessionalResponse> list(@RequestParam UUID organizationId,
                                           @RequestParam(required = false) String specialtyCode) {
        return service.list(organizationId, specialtyCode);
    }

    /** Contrato de Agenda: valida que el profesional existe y conserva un vínculo activo. */
    @GetMapping("/internal/v1/professionals/{id}")
    public ProfessionalResponse findActive(@PathVariable UUID id) { return service.findActive(id); }
}
