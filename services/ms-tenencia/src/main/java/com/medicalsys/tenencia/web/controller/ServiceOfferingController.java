package com.medicalsys.tenencia.web.controller;

import com.medicalsys.tenencia.common.security.RequiresRole;
import com.medicalsys.tenencia.common.security.Role;
import com.medicalsys.tenencia.common.tenant.RequestContextHolder;
import com.medicalsys.tenencia.service.ServiceOfferingService;
import com.medicalsys.tenencia.web.dto.ChangeServiceOfferingActiveRequest;
import com.medicalsys.tenencia.web.dto.CreateServiceOfferingRequest;
import com.medicalsys.tenencia.web.dto.PageResponse;
import com.medicalsys.tenencia.web.dto.ServiceOfferingResponse;
import com.medicalsys.tenencia.web.dto.UpdateServiceOfferingRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogo de prestaciones de la organizacion del token.
 *
 * <p>Es el catalogo del tenant, no el catalogo global: las especialidades y los codigos CIE-10
 * viven en ms-catalogos y aqui solo se referencian por codigo.
 */
@RestController
@RequestMapping("/api/v1/tenancy/prestaciones")
public class ServiceOfferingController {

    private final ServiceOfferingService service;

    public ServiceOfferingController(ServiceOfferingService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ResponseEntity<ServiceOfferingResponse> create(
            @Valid @RequestBody CreateServiceOfferingRequest request) {
        ServiceOfferingResponse offering = service.create(RequestContextHolder.require(), request);
        return ResponseEntity.created(URI.create("/api/v1/tenancy/prestaciones/" + offering.id())).body(offering);
    }

    /**
     * Lista el catalogo del tenant.
     *
     * @param active        filtra por vigencia; ausente devuelve activas e inactivas
     * @param specialtyCode filtra por especialidad de ms-catalogos
     */
    @GetMapping
    @RequiresRole({Role.ADMINISTRATOR, Role.RECEPTIONIST, Role.BILLING_CLERK, Role.PHYSICIAN, Role.PLATFORM_ADMIN})
    public PageResponse<ServiceOfferingResponse> list(@RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String specialtyCode,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(service.list(RequestContextHolder.require(), active, specialtyCode, pageable));
    }

    @GetMapping("/{serviceOfferingId}")
    @RequiresRole({Role.ADMINISTRATOR, Role.RECEPTIONIST, Role.BILLING_CLERK, Role.PHYSICIAN, Role.PLATFORM_ADMIN})
    public ServiceOfferingResponse get(@PathVariable UUID serviceOfferingId) {
        return service.get(RequestContextHolder.require(), serviceOfferingId);
    }

    @PutMapping("/{serviceOfferingId}")
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ServiceOfferingResponse update(@PathVariable UUID serviceOfferingId,
            @Valid @RequestBody UpdateServiceOfferingRequest request) {
        return service.update(RequestContextHolder.require(), serviceOfferingId, request);
    }

    /** Activa o desactiva la prestacion; desactivar no borra la fila. */
    @PatchMapping("/{serviceOfferingId}/vigencia")
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ServiceOfferingResponse changeActive(@PathVariable UUID serviceOfferingId,
            @Valid @RequestBody ChangeServiceOfferingActiveRequest request) {
        return service.changeActive(RequestContextHolder.require(), serviceOfferingId, request);
    }
}
