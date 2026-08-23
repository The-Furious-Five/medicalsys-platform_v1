package com.medicalsys.historial.web.controller;

import com.medicalsys.historial.common.security.RequiresRole;
import com.medicalsys.historial.common.security.Role;
import com.medicalsys.historial.common.tenant.RequestContextHolder;
import com.medicalsys.historial.service.PrescriptionService;
import com.medicalsys.historial.web.dto.AttachDocumentRequest;
import com.medicalsys.historial.web.dto.ChangePrescriptionStatusRequest;
import com.medicalsys.historial.web.dto.CreatePrescriptionRequest;
import com.medicalsys.historial.web.dto.PrescriptionResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Recetas emitidas durante un encuentro. */
@RestController
@RequestMapping("/api/v1")
public class PrescriptionController {

    private final PrescriptionService service;

    public PrescriptionController(PrescriptionService service) {
        this.service = service;
    }

    @PostMapping("/encuentros/{encounterId}/recetas")
    @RequiresRole({Role.PHYSICIAN, Role.PLATFORM_ADMIN})
    public ResponseEntity<PrescriptionResponse> create(@PathVariable UUID encounterId,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        PrescriptionResponse body = service.create(RequestContextHolder.require(), encounterId, request);
        return ResponseEntity.created(URI.create("/api/v1/recetas/" + body.id())).body(body);
    }

    @GetMapping("/encuentros/{encounterId}/recetas")
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public List<PrescriptionResponse> listByEncounter(@PathVariable UUID encounterId) {
        return service.listByEncounter(RequestContextHolder.require(), encounterId);
    }

    @GetMapping("/recetas/{prescriptionId}")
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public PrescriptionResponse get(@PathVariable UUID prescriptionId) {
        return service.get(RequestContextHolder.require(), prescriptionId);
    }

    @PatchMapping("/recetas/{prescriptionId}/estado")
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public PrescriptionResponse changeStatus(@PathVariable UUID prescriptionId,
            @Valid @RequestBody ChangePrescriptionStatusRequest request) {
        return service.changeStatus(RequestContextHolder.require(), prescriptionId, request);
    }

    /** Vincula el PDF firmado que produjo ms-documental. */
    @PutMapping("/recetas/{prescriptionId}/documento")
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public PrescriptionResponse attachDocument(@PathVariable UUID prescriptionId,
            @Valid @RequestBody AttachDocumentRequest request) {
        return service.attachDocument(RequestContextHolder.require(), prescriptionId, request);
    }
}
