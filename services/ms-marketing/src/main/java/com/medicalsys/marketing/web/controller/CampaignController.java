package com.medicalsys.marketing.web.controller;

import com.medicalsys.marketing.common.security.RequiresRole;
import com.medicalsys.marketing.common.security.Role;
import com.medicalsys.marketing.common.tenant.RequestContextHolder;
import com.medicalsys.marketing.domain.enums.CampaignStatus;
import com.medicalsys.marketing.domain.enums.RecipientStatus;
import com.medicalsys.marketing.service.CampaignService;
import com.medicalsys.marketing.web.dto.CampaignPerformanceResponse;
import com.medicalsys.marketing.web.dto.CampaignRecipientResponse;
import com.medicalsys.marketing.web.dto.CampaignResponse;
import com.medicalsys.marketing.web.dto.CampaignSummaryResponse;
import com.medicalsys.marketing.web.dto.CreateCampaignRequest;
import com.medicalsys.marketing.web.dto.MaterializeAudienceResponse;
import com.medicalsys.marketing.web.dto.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
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
 * Campanas comerciales y de salud preventiva.
 *
 * <p>El ciclo esta expuesto como cuatro operaciones separadas -- crear, materializar, lanzar, medir --
 * y no como un unico "enviar". Entre materializar y lanzar cabe la revision humana de a cuanta gente
 * va a llegar el mensaje, que es la unica proteccion real contra un criterio mal escrito.
 *
 * <p>Lanzar publica {@code marketing.campana}. Este servicio no entrega mensajes: eso es de
 * ms-notificaciones, que tiene las credenciales del proveedor y las reglas de la ventana de 24 h.
 */
@RestController
@RequestMapping("/api/v1/campanas")
public class CampaignController {

    private final CampaignService service;

    public CampaignController(CampaignService service) {
        this.service = service;
    }

    /** Crea una campana en BORRADOR. */
    @PostMapping
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CreateCampaignRequest request) {
        CampaignResponse campaign = service.create(RequestContextHolder.require(), request);
        return ResponseEntity.created(URI.create("/api/v1/campanas/" + campaign.id())).body(campaign);
    }

    /** Lista las campanas del tenant. */
    @GetMapping
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN, Role.RECEPTIONIST})
    public PageResponse<CampaignSummaryResponse> list(@RequestParam(required = false) CampaignStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.list(RequestContextHolder.require(), status, pageable));
    }

    /** Devuelve una campana con sus contadores. */
    @GetMapping("/{campaignId}")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN, Role.RECEPTIONIST})
    public CampaignResponse get(@PathVariable UUID campaignId) {
        return service.get(RequestContextHolder.require(), campaignId);
    }

    /** Congela la audiencia: quien recibira la campana y quien queda excluido, con su motivo. */
    @PostMapping("/{campaignId}/audiencia")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public MaterializeAudienceResponse materializeAudience(@PathVariable UUID campaignId) {
        return service.materializeAudience(RequestContextHolder.require(), campaignId);
    }

    /** Programa la campana para su fecha de inicio. */
    @PostMapping("/{campaignId}/programacion")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public CampaignResponse schedule(@PathVariable UUID campaignId) {
        return service.schedule(RequestContextHolder.require(), campaignId);
    }

    /** Lanza la campana y publica {@code marketing.campana} en la misma transaccion. */
    @PostMapping("/{campaignId}/lanzamiento")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public CampaignResponse launch(@PathVariable UUID campaignId) {
        return service.launch(RequestContextHolder.require(), campaignId);
    }

    /** Pausa la campana. */
    @PostMapping("/{campaignId}/pausa")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public CampaignResponse pause(@PathVariable UUID campaignId) {
        return service.pause(RequestContextHolder.require(), campaignId);
    }

    /** Cierra la campana. */
    @PostMapping("/{campaignId}/finalizacion")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public CampaignResponse finish(@PathVariable UUID campaignId) {
        return service.finish(RequestContextHolder.require(), campaignId);
    }

    /** Cancela la campana. */
    @PostMapping("/{campaignId}/cancelacion")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public CampaignResponse cancel(@PathVariable UUID campaignId) {
        return service.cancel(RequestContextHolder.require(), campaignId);
    }

    /** Lista los destinatarios materializados con su resultado individual. */
    @GetMapping("/{campaignId}/destinatarios")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public PageResponse<CampaignRecipientResponse> recipients(@PathVariable UUID campaignId,
            @RequestParam(required = false) RecipientStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(service.listRecipients(RequestContextHolder.require(), campaignId, status,
                pageable));
    }

    /** Rendimiento con las tasas calculadas por la vista de la base. */
    @GetMapping("/{campaignId}/rendimiento")
    @RequiresRole({Role.MARKETING_MANAGER, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN, Role.RECEPTIONIST})
    public CampaignPerformanceResponse performance(@PathVariable UUID campaignId) {
        return service.performance(RequestContextHolder.require(), campaignId);
    }
}
