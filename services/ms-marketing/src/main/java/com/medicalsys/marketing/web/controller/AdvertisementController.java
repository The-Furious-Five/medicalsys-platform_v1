package com.medicalsys.marketing.web.controller;

import com.medicalsys.marketing.common.security.RequiresRole;
import com.medicalsys.marketing.common.security.Role;
import com.medicalsys.marketing.common.tenant.RequestContextHolder;
import com.medicalsys.marketing.domain.enums.AdvertisementPlacement;
import com.medicalsys.marketing.service.AdvertisementService;
import com.medicalsys.marketing.web.dto.AdvertisementResponse;
import com.medicalsys.marketing.web.dto.CreateAdvertisementRequest;
import com.medicalsys.marketing.web.dto.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anuncios del portal del paciente.
 *
 * <p>{@code /vigentes}, {@code /impresiones} y {@code /clics} admiten el rol PATIENT: son las tres
 * operaciones que el portal necesita. Ninguna devuelve metricas de negocio de otros pacientes ni
 * permite administrar nada.
 */
@RestController
@RequestMapping("/api/v1/anuncios")
public class AdvertisementController {

    private final AdvertisementService service;

    public AdvertisementController(AdvertisementService service) {
        this.service = service;
    }

    /** Crea un anuncio en BORRADOR. */
    @PostMapping
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ResponseEntity<AdvertisementResponse> create(@Valid @RequestBody CreateAdvertisementRequest request) {
        AdvertisementResponse advertisement = service.create(RequestContextHolder.require(), request);
        return ResponseEntity.created(URI.create("/api/v1/anuncios/" + advertisement.id())).body(advertisement);
    }

    /** Lista los anuncios visibles para el tenant, incluidos los institucionales. */
    @GetMapping
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN, Role.RECEPTIONIST})
    public PageResponse<AdvertisementResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.list(RequestContextHolder.require(), pageable));
    }

    /** Anuncios que el portal debe mostrar ahora, ordenados por prioridad. */
    @GetMapping("/vigentes")
    @RequiresRole({Role.PATIENT, Role.RECEPTIONIST, Role.MARKETING_MANAGER, Role.ADMINISTRATOR,
            Role.PLATFORM_ADMIN})
    public List<AdvertisementResponse> live(@RequestParam(required = false) AdvertisementPlacement ubicacion) {
        return service.live(RequestContextHolder.require(), ubicacion);
    }

    /** Devuelve un anuncio visible para el tenant. */
    @GetMapping("/{advertisementId}")
    @RequiresRole({Role.PATIENT, Role.RECEPTIONIST, Role.MARKETING_MANAGER, Role.ADMINISTRATOR,
            Role.PLATFORM_ADMIN})
    public AdvertisementResponse get(@PathVariable UUID advertisementId) {
        return service.get(RequestContextHolder.require(), advertisementId);
    }

    /** Publica el anuncio. */
    @PostMapping("/{advertisementId}/publicacion")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public AdvertisementResponse publish(@PathVariable UUID advertisementId) {
        return service.publish(RequestContextHolder.require(), advertisementId);
    }

    /** Retira temporalmente el anuncio. */
    @PostMapping("/{advertisementId}/pausa")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public AdvertisementResponse pause(@PathVariable UUID advertisementId) {
        return service.pause(RequestContextHolder.require(), advertisementId);
    }

    /** Cierra el anuncio de forma definitiva. */
    @PostMapping("/{advertisementId}/finalizacion")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public AdvertisementResponse finish(@PathVariable UUID advertisementId) {
        return service.finish(RequestContextHolder.require(), advertisementId);
    }

    /** Suma una impresion. La cuenta la lleva el servidor, no el cliente. */
    @PostMapping("/{advertisementId}/impresiones")
    @RequiresRole({Role.PATIENT, Role.RECEPTIONIST, Role.MARKETING_MANAGER, Role.ADMINISTRATOR,
            Role.PLATFORM_ADMIN})
    public AdvertisementResponse registerImpression(@PathVariable UUID advertisementId) {
        return service.registerImpression(RequestContextHolder.require(), advertisementId);
    }

    /** Suma un clic. */
    @PostMapping("/{advertisementId}/clics")
    @RequiresRole({Role.PATIENT, Role.RECEPTIONIST, Role.MARKETING_MANAGER, Role.ADMINISTRATOR,
            Role.PLATFORM_ADMIN})
    public AdvertisementResponse registerClick(@PathVariable UUID advertisementId) {
        return service.registerClick(RequestContextHolder.require(), advertisementId);
    }
}
