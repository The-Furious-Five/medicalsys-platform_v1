package com.medicalsys.tenencia.web.controller;

import com.medicalsys.tenencia.common.security.RequiresPermission;
import com.medicalsys.tenencia.service.SubscriptionService;
import com.medicalsys.tenencia.web.dto.SubscriptionPlanResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogo de planes comerciales de la plataforma.
 *
 * <p>Es el unico recurso de este servicio que no pertenece a un tenant: los planes son globales.
 * Aun asi exige identidad, porque la lista de precios no es publica.
 */
@RestController
@RequestMapping("/api/v1/tenancy/planes")
public class SubscriptionPlanController {

    private final SubscriptionService service;

    public SubscriptionPlanController(SubscriptionService service) {
        this.service = service;
    }

    /** Planes activos, del mas barato al mas caro. Un limite nulo significa ilimitado. */
    @GetMapping
    @RequiresPermission("tenancy.plan.read")
    public List<SubscriptionPlanResponse> list() {
        return service.listActivePlans();
    }
}
