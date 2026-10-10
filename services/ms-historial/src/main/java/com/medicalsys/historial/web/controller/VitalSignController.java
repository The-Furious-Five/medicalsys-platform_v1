package com.medicalsys.historial.web.controller;

import com.medicalsys.historial.common.security.RequiresPermission;
import com.medicalsys.historial.common.tenant.RequestContextHolder;
import com.medicalsys.historial.service.VitalSignService;
import com.medicalsys.historial.web.dto.CreateVitalSignRequest;
import com.medicalsys.historial.web.dto.VitalSignResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Signos vitales del encuentro. */
@RestController
@RequestMapping("/api/v1/encuentros/{encounterId}/signos-vitales")
public class VitalSignController {

    private final VitalSignService service;

    public VitalSignController(VitalSignService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("clinical.vital_sign.create")
    public VitalSignResponse create(@PathVariable UUID encounterId,
            @Valid @RequestBody CreateVitalSignRequest request) {
        return service.create(RequestContextHolder.require(), encounterId, request);
    }

    @GetMapping
    @RequiresPermission("clinical.vital_sign.read")
    public List<VitalSignResponse> list(@PathVariable UUID encounterId) {
        return service.list(RequestContextHolder.require(), encounterId);
    }
}
