package com.medicalsys.tenencia.web.controller;

import com.medicalsys.tenencia.common.security.RequiresPermission;
import com.medicalsys.tenencia.common.tenant.RequestContextHolder;
import com.medicalsys.tenencia.service.OrganizationService;
import com.medicalsys.tenencia.web.dto.ChangeOrganizationStatusRequest;
import com.medicalsys.tenencia.web.dto.CreateOrganizationRequest;
import com.medicalsys.tenencia.web.dto.OrganizationResponse;
import com.medicalsys.tenencia.web.dto.UpdateOrganizationRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organizacion del tenant autenticado.
 *
 * <p>Las rutas de lectura y edicion operan siempre sobre la organizacion del token: no reciben un
 * identificador de tenant, porque aceptarlo permitiria leer la configuracion de otra clinica.
 *
 * <p>El alta es la excepcion: crea un tenant nuevo y por eso la reserva PLATFORM_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/tenancy/organizacion")
public class OrganizationController {

    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    /** Da de alta un tenant con su suscripcion de prueba y sus contadores de cuota. */
    @PostMapping
    @RequiresPermission("tenancy.organization.create")
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse organization = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tenancy/organizacion")).body(organization);
    }

    /** Devuelve la ficha de la organizacion del token. */
    @GetMapping
    @RequiresPermission("tenancy.organization.read")
    public OrganizationResponse getCurrent() {
        return service.getCurrent(RequestContextHolder.require());
    }

    /** Actualiza los datos administrativos; code y nit no se editan. */
    @PutMapping
    @RequiresPermission("tenancy.organization.update")
    public OrganizationResponse update(@Valid @RequestBody UpdateOrganizationRequest request) {
        return service.update(RequestContextHolder.require(), request);
    }

    /** Cambia el estado del tenant; activar exige una suscripcion vigente. */
    @PatchMapping("/estado")
    @RequiresPermission("tenancy.organization.change_status")
    public OrganizationResponse changeStatus(@Valid @RequestBody ChangeOrganizationStatusRequest request) {
        return service.changeStatus(RequestContextHolder.require(), request);
    }
}
