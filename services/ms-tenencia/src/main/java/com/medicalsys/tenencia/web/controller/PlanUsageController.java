package com.medicalsys.tenencia.web.controller;

import com.medicalsys.tenencia.common.security.RequiresRole;
import com.medicalsys.tenencia.common.security.Role;
import com.medicalsys.tenencia.common.tenant.RequestContextHolder;
import com.medicalsys.tenencia.service.PlanUsageService;
import com.medicalsys.tenencia.web.dto.PlanUsageResponse;
import com.medicalsys.tenencia.web.dto.RecordUsageRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuotas consumidas por la organizacion del token.
 *
 * <p>El consumo lo reporta el servicio propietario del recurso: identidad al alta de un usuario y
 * documental al guardar un archivo. Tenencia solo materializa el valor y lo contrasta con el
 * limite del plan contratado.
 */
@RestController
@RequestMapping("/api/v1/tenancy/consumo")
public class PlanUsageController {

    private final PlanUsageService service;

    public PlanUsageController(PlanUsageService service) {
        this.service = service;
    }

    /** Consumo de las tres metricas frente a los limites del plan. */
    @GetMapping
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public List<PlanUsageResponse> list() {
        return service.list(RequestContextHolder.require());
    }

    /** Registra un incremento relativo o un recuento absoluto de una metrica. */
    @PostMapping
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public PlanUsageResponse record(@Valid @RequestBody RecordUsageRequest request) {
        return service.record(RequestContextHolder.require(), request);
    }
}
