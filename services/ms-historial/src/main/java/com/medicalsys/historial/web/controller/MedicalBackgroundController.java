package com.medicalsys.historial.web.controller;

import com.medicalsys.historial.common.security.RequiresPermission;
import com.medicalsys.historial.common.tenant.RequestContextHolder;
import com.medicalsys.historial.domain.enums.BackgroundType;
import com.medicalsys.historial.service.MedicalBackgroundService;
import com.medicalsys.historial.web.dto.CreateMedicalBackgroundRequest;
import com.medicalsys.historial.web.dto.MedicalBackgroundResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Antecedentes del expediente. */
@RestController
@RequestMapping("/api/v1")
public class MedicalBackgroundController {

    private final MedicalBackgroundService service;

    public MedicalBackgroundController(MedicalBackgroundService service) {
        this.service = service;
    }

    @PostMapping("/expedientes/{recordId}/antecedentes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("clinical.background.create")
    public MedicalBackgroundResponse create(@PathVariable UUID recordId,
            @Valid @RequestBody CreateMedicalBackgroundRequest request) {
        return service.create(RequestContextHolder.require(), recordId, request);
    }

    @GetMapping("/expedientes/{recordId}/antecedentes")
    @RequiresPermission("clinical.background.read")
    public List<MedicalBackgroundResponse> list(@PathVariable UUID recordId,
            @RequestParam(required = false) BackgroundType type) {
        return service.list(RequestContextHolder.require(), recordId, type);
    }

    /** Marca el antecedente como no vigente; el registro clinico nunca se borra. */
    @DeleteMapping("/antecedentes/{backgroundId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission("clinical.background.deactivate")
    public void deactivate(@PathVariable Long backgroundId) {
        service.deactivate(RequestContextHolder.require(), backgroundId);
    }
}
