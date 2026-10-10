package com.medicalsys.tenencia.web.controller;

import com.medicalsys.tenencia.common.security.RequiresPermission;
import com.medicalsys.tenencia.common.tenant.RequestContextHolder;
import com.medicalsys.tenencia.service.OrganizationService;
import com.medicalsys.tenencia.service.SubscriptionService;
import com.medicalsys.tenencia.web.dto.ChangeSubscriptionStatusRequest;
import com.medicalsys.tenencia.web.dto.CreateSubscriptionRequest;
import com.medicalsys.tenencia.web.dto.SubscriptionResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Suscripcion de la organizacion del token.
 *
 * <p>Una organizacion tiene como mucho un contrato no cancelado: contratar otro con uno vigente
 * devuelve 409.
 */
@RestController
@RequestMapping("/api/v1/tenancy/suscripcion")
public class SubscriptionController {

    private final SubscriptionService subscriptions;
    private final OrganizationService organizations;

    public SubscriptionController(SubscriptionService subscriptions, OrganizationService organizations) {
        this.subscriptions = subscriptions;
        this.organizations = organizations;
    }

    /** Contrata un plan para el tenant. */
    @PostMapping
    @RequiresPermission("tenancy.subscription.subscribe")
    public ResponseEntity<SubscriptionResponse> subscribe(@Valid @RequestBody CreateSubscriptionRequest request) {
        var context = RequestContextHolder.require();
        SubscriptionResponse subscription = subscriptions.subscribe(context,
                organizations.require(context.organizationId()), request);
        return ResponseEntity.created(URI.create("/api/v1/tenancy/suscripcion")).body(subscription);
    }

    /** Devuelve la suscripcion vigente y si habilita el uso del producto hoy. */
    @GetMapping
    @RequiresPermission("tenancy.subscription.read")
    public SubscriptionResponse getCurrent() {
        return subscriptions.getCurrent(RequestContextHolder.require());
    }

    /** Historial de contratos del tenant, del mas reciente al mas antiguo. */
    @GetMapping("/historial")
    @RequiresPermission("tenancy.subscription.read_history")
    public List<SubscriptionResponse> history() {
        return subscriptions.listHistory(RequestContextHolder.require());
    }

    /** Cambia el estado del contrato; cancelar cierra tambien su vigencia. */
    @PatchMapping("/estado")
    @RequiresPermission("tenancy.subscription.change_status")
    public SubscriptionResponse changeStatus(@Valid @RequestBody ChangeSubscriptionStatusRequest request) {
        return subscriptions.changeStatus(RequestContextHolder.require(), request);
    }
}
