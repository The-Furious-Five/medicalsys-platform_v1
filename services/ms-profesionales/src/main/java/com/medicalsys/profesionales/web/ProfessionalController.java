package com.medicalsys.profesionales.web;

import com.medicalsys.profesionales.common.tenant.RequestContextHolder;
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
    public List<ProfessionalResponse> list(@RequestParam(required = false) String specialtyCode) {
        UUID organizationId = RequestContextHolder.require().organizationId();
        return service.list(organizationId, specialtyCode);
    }

    /** Ficha completa, en cualquier estado: es la pantalla desde la que se revierte una baja. */
    @GetMapping("/api/v1/professionals/{id}")
    public ProfessionalResponse get(@PathVariable UUID id) {
        UUID organizationId = RequestContextHolder.require().organizationId();
        return service.get(organizationId, id);
    }

    @PatchMapping("/api/v1/professionals/{id}")
    public ProfessionalResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateProfessionalRequest request) {
        return service.update(id, request);
    }

    /** Activa, suspende o termina el vínculo del profesional con un centro. */
    @PatchMapping("/api/v1/professionals/{id}/organizations/{linkId}")
    public ProfessionalResponse changeLinkStatus(@PathVariable UUID id, @PathVariable UUID linkId,
                                                 @Valid @RequestBody UpdateLinkStatusRequest request) {
        UUID organizationId = RequestContextHolder.require().organizationId();
        return service.changeLinkStatus(organizationId, id, linkId, request.status());
    }

    /** Contrato de Agenda: valida que el profesional existe y conserva un vínculo activo. */
    @GetMapping("/internal/v1/professionals/{id}")
    public ProfessionalResponse findActive(@PathVariable UUID id) { return service.findActive(id); }
}
