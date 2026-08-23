package com.medicalsys.historial.web.controller;

import com.medicalsys.historial.common.security.RequiresRole;
import com.medicalsys.historial.common.security.Role;
import com.medicalsys.historial.common.tenant.RequestContextHolder;
import com.medicalsys.historial.service.PerformedProcedureService;
import com.medicalsys.historial.web.dto.CreateProcedureRequest;
import com.medicalsys.historial.web.dto.PerformedProcedureResponse;
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

/** Procedimientos realizados durante el encuentro. */
@RestController
@RequestMapping("/api/v1/encuentros/{encounterId}/procedimientos")
public class PerformedProcedureController {

    private final PerformedProcedureService service;

    public PerformedProcedureController(PerformedProcedureService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole({Role.PHYSICIAN, Role.PLATFORM_ADMIN})
    public PerformedProcedureResponse create(@PathVariable UUID encounterId,
            @Valid @RequestBody CreateProcedureRequest request) {
        return service.create(RequestContextHolder.require(), encounterId, request);
    }

    @GetMapping
    @RequiresRole({Role.PHYSICIAN, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public List<PerformedProcedureResponse> list(@PathVariable UUID encounterId) {
        return service.list(RequestContextHolder.require(), encounterId);
    }
}
