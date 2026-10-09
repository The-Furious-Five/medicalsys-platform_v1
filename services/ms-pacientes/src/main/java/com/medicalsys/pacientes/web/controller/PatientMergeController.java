package com.medicalsys.pacientes.web.controller;

import com.medicalsys.pacientes.common.security.RequiresRole;
import com.medicalsys.pacientes.common.security.Role;
import com.medicalsys.pacientes.common.tenant.RequestContextHolder;
import com.medicalsys.pacientes.service.PatientMergeService;
import com.medicalsys.pacientes.web.dto.MergePatientRequest;
import com.medicalsys.pacientes.web.dto.PatientMergeResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operaciones administrativas de unificación de identidades duplicadas. */
@RestController
@RequestMapping("/api/v1/patients/{survivingPatientId}/merges")
public class PatientMergeController {

    private final PatientMergeService merges;

    public PatientMergeController(PatientMergeService merges) {
        this.merges = merges;
    }

    @PostMapping
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ResponseEntity<PatientMergeResponse> merge(@PathVariable UUID survivingPatientId,
            @Valid @RequestBody MergePatientRequest request) {
        UUID userId = RequestContextHolder.requireUserId();
        PatientMergeResponse response = merges.merge(survivingPatientId, request, userId);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + survivingPatientId + "/merges/"
                + response.eventId())).body(response);
    }

    @GetMapping
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public List<PatientMergeResponse> history(@PathVariable UUID survivingPatientId) {
        return merges.history(survivingPatientId);
    }
}
