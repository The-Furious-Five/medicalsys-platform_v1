package com.medicalsys.historial.web.controller;

import com.medicalsys.historial.common.security.RequiresPermission;
import com.medicalsys.historial.common.tenant.RequestContextHolder;
import com.medicalsys.historial.service.DiagnosisService;
import com.medicalsys.historial.web.dto.CreateDiagnosisRequest;
import com.medicalsys.historial.web.dto.DiagnosisResponse;
import com.medicalsys.historial.web.dto.UpdateDiagnosisRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diagnosticos del encuentro.
 *
 * <p>Un codigo CIE-10 inexistente se rechaza con 422 ICD10_CODE_NOT_ALLOWED antes de tocar la base.
 */
@RestController
@RequestMapping("/api/v1")
public class DiagnosisController {

    private final DiagnosisService service;

    public DiagnosisController(DiagnosisService service) {
        this.service = service;
    }

    @PostMapping("/encuentros/{encounterId}/diagnosticos")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("clinical.diagnosis.create")
    public DiagnosisResponse create(@PathVariable UUID encounterId,
            @Valid @RequestBody CreateDiagnosisRequest request) {
        return service.create(RequestContextHolder.require(), encounterId, request);
    }

    @GetMapping("/encuentros/{encounterId}/diagnosticos")
    @RequiresPermission("clinical.diagnosis.read")
    public List<DiagnosisResponse> list(@PathVariable UUID encounterId) {
        return service.list(RequestContextHolder.require(), encounterId);
    }

    @PatchMapping("/diagnosticos/{diagnosisId}")
    @RequiresPermission("clinical.diagnosis.update")
    public DiagnosisResponse update(@PathVariable Long diagnosisId,
            @Valid @RequestBody UpdateDiagnosisRequest request) {
        return service.update(RequestContextHolder.require(), diagnosisId, request);
    }
}
