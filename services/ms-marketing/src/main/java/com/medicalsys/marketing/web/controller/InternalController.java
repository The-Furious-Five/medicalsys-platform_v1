package com.medicalsys.marketing.web.controller;

import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.common.error.NotFoundException;
import com.medicalsys.marketing.common.security.InternalEndpoint;
import com.medicalsys.marketing.common.tenant.RequestContextHolder;
import com.medicalsys.marketing.domain.enums.OutboxStatus;
import com.medicalsys.marketing.domain.enums.RecipientStatus;
import com.medicalsys.marketing.domain.model.OutboxEvent;
import com.medicalsys.marketing.event.EventIngestionService;
import com.medicalsys.marketing.mapper.MarketingMapper;
import com.medicalsys.marketing.repository.OutboxEventRepository;
import com.medicalsys.marketing.service.CampaignService;
import com.medicalsys.marketing.service.PromotionService;
import com.medicalsys.marketing.web.dto.CampaignRecipientResponse;
import com.medicalsys.marketing.web.dto.CouponValidationResponse;
import com.medicalsys.marketing.web.dto.EventAcceptedResponse;
import com.medicalsys.marketing.web.dto.IncomingEventRequest;
import com.medicalsys.marketing.web.dto.OutboxEventResponse;
import com.medicalsys.marketing.web.dto.PageResponse;
import com.medicalsys.marketing.web.dto.ValidateCouponRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rutas internas consumidas por otros microservicios dentro de la red medicalsys-interna.
 *
 * <p>Tres grupos, y conviene distinguirlos:
 * <ul>
 *   <li><b>Entrada de eventos.</b> Sustituye al broker que el MVP todavia no tiene. Todo cuerpo pasa
 *       por el guardian de privacidad de {@link EventIngestionService} antes de proyectarse: un
 *       productor que incluyera un diagnostico recibe 422 y el intento queda en el log.</li>
 *   <li><b>Cupones.</b> El unico contrato sincrono que ms-facturacion consume.</li>
 *   <li><b>Salida de eventos.</b> Permite drenar el outbox desde fuera mientras no exista broker, y
 *       ensenar en la demostracion que lanzar una campana produjo de verdad un
 *       {@code marketing.campana}.</li>
 * </ul>
 *
 * <p>Ninguna ruta de aqui devuelve dato clinico, porque no hay ninguno que devolver.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    private final EventIngestionService events;
    private final PromotionService promotions;
    private final CampaignService campaigns;
    private final OutboxEventRepository outbox;
    private final MarketingMapper mapper;
    private final Clock clock;

    public InternalController(EventIngestionService events, PromotionService promotions,
            CampaignService campaigns, OutboxEventRepository outbox, MarketingMapper mapper, Clock clock) {
        this.events = events;
        this.promotions = promotions;
        this.campaigns = campaigns;
        this.outbox = outbox;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Recibe un evento de otro servicio.
     *
     * <p>Esta ruta es un transporte, no la barrera. La inspeccion de privacidad la hace
     * {@link EventIngestionService}, de modo que un evento que en el futuro llegue por un broker pase
     * por la misma comprobacion sin depender de que alguien la duplique aqui.
     */
    @PostMapping("/eventos")
    @InternalEndpoint
    public EventAcceptedResponse ingest(@Valid @RequestBody IncomingEventRequest request) {
        return events.apply(request);
    }

    /**
     * Valida un cupon. Contrato congelado con ms-facturacion.
     *
     * <p>Un cupon que no aplica responde 200 con {@code valid=false}; ms-facturacion emite igual, sin
     * descuento. Un 503 de esta ruta significa lo mismo para el cobro: se emite sin cupon. Marketing
     * es el servicio de menor criticidad del sistema y su caida no debe impedir facturar.
     */
    @PostMapping("/promociones/validar")
    @InternalEndpoint
    public CouponValidationResponse validateCoupon(@Valid @RequestBody ValidateCouponRequest request) {
        return promotions.validate(RequestContextHolder.require(), request);
    }

    /**
     * Audiencia de una campana, para que ms-notificaciones la entregue.
     *
     * <p>Es la contrapartida del claim check: el evento {@code marketing.campana} no lleva los
     * destinatarios, se piden por aqui. Meter miles de telefonos en un mensaje publicaria datos de
     * contacto en un canal que otros servicios pueden leer.
     */
    @GetMapping("/campanas/{campaignId}/destinatarios")
    @InternalEndpoint
    public PageResponse<CampaignRecipientResponse> recipients(@PathVariable UUID campaignId,
            @PageableDefault(size = 200) Pageable pageable) {
        return PageResponse.of(campaigns.listRecipients(RequestContextHolder.require(), campaignId,
                RecipientStatus.SELECTED, pageable));
    }

    /**
     * Eventos salientes pendientes de publicar.
     *
     * <p>Existe para que un relay externo pueda drenar el outbox mientras la plataforma no tenga
     * broker. Con {@code MARKETING_OUTBOX_MODE=RETAIN} los eventos se quedan aqui hasta que alguien
     * los tome.
     */
    @GetMapping("/eventos/salientes")
    @InternalEndpoint
    public List<OutboxEventResponse> pendingOutbox(
            @RequestParam(required = false, defaultValue = "PENDING") OutboxStatus estado,
            @RequestParam(required = false, defaultValue = "100") int limite) {
        return outbox.findByStatusInOrderByIdAsc(List.of(estado), PageRequest.of(0, Math.min(limite, 500)))
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Eventos salientes de una campana concreta; util para la demostracion y para depurar. */
    @GetMapping("/campanas/{campaignId}/eventos")
    @InternalEndpoint
    public List<OutboxEventResponse> outboxOfCampaign(@PathVariable UUID campaignId) {
        // Se comprueba la pertenencia al tenant antes de exponer el evento.
        campaigns.require(RequestContextHolder.require().organizationId(), campaignId);
        return outbox.findByAggregatePublicIdOrderByIdAsc(campaignId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Marca un evento como publicado, para el relay externo del modo RETAIN. */
    @PostMapping("/eventos/salientes/{eventId}/publicado")
    @Transactional
    @InternalEndpoint
    public OutboxEventResponse markPublished(@PathVariable UUID eventId) {
        OutboxEvent event = outbox.findByEventPublicId(eventId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                        "El evento saliente '" + eventId + "' no existe"));
        event.markPublished(clock.instant());
        return mapper.toResponse(event);
    }
}
