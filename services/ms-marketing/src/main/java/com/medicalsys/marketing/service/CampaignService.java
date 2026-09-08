package com.medicalsys.marketing.service;

import com.medicalsys.marketing.common.DateTimes;
import com.medicalsys.marketing.common.error.BusinessRuleException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.common.error.NotFoundException;
import com.medicalsys.marketing.common.tenant.RequestContext;
import com.medicalsys.marketing.config.MarketingProperties;
import com.medicalsys.marketing.domain.enums.CampaignStatus;
import com.medicalsys.marketing.domain.enums.RecipientStatus;
import com.medicalsys.marketing.domain.model.AudienceSegment;
import com.medicalsys.marketing.domain.model.Campaign;
import com.medicalsys.marketing.domain.model.CampaignRecipient;
import com.medicalsys.marketing.domain.model.Promotion;
import com.medicalsys.marketing.domain.model.RefPatient;
import com.medicalsys.marketing.event.MarketingEventPublisher;
import com.medicalsys.marketing.mapper.MarketingMapper;
import com.medicalsys.marketing.repository.AudienceQueryRepository;
import com.medicalsys.marketing.repository.AudienceQueryRepository.AudienceCandidate;
import com.medicalsys.marketing.repository.CampaignRecipientRepository;
import com.medicalsys.marketing.repository.CampaignRepository;
import com.medicalsys.marketing.repository.MarketingViewRepository;
import com.medicalsys.marketing.repository.MarketingViewRepository.CampaignPerformanceRow;
import com.medicalsys.marketing.repository.RefPatientRepository;
import com.medicalsys.marketing.web.dto.CampaignPerformanceResponse;
import com.medicalsys.marketing.web.dto.CampaignRecipientResponse;
import com.medicalsys.marketing.web.dto.CampaignResponse;
import com.medicalsys.marketing.web.dto.CampaignSummaryResponse;
import com.medicalsys.marketing.web.dto.CreateCampaignRequest;
import com.medicalsys.marketing.web.dto.MaterializeAudienceResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de la campana: definirla, materializar su audiencia, lanzarla y medirla.
 *
 * <p>El ciclo tiene cuatro pasos deliberadamente separados -- crear, materializar, lanzar, medir --
 * en lugar de un unico "enviar campana". La separacion no es burocracia: entre materializar y lanzar
 * hay una decision humana que revisa a cuanta gente va a llegar el mensaje, y esa revision es la
 * unica proteccion real contra un criterio mal escrito.
 *
 * <p>Este servicio no envia nada. Publica {@code marketing.campana} y ms-notificaciones decide
 * cuando y como entrega, con sus propias reglas de opt-in y de ventana de 24 horas. Que Meta este
 * caido no puede impedir que aqui se lance una campana, ni al reves.
 */
@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    /** Motivo registrado al excluir a un paciente que pidio no recibir publicidad. */
    private static final String OPT_OUT_REASON = "El paciente pidio no recibir comunicaciones comerciales";

    private final CampaignRepository campaigns;
    private final CampaignRecipientRepository recipients;
    private final RefPatientRepository patients;
    private final AudienceQueryRepository audiences;
    private final MarketingViewRepository views;
    private final SegmentService segments;
    private final PromotionService promotions;
    private final MarketingEntitlementService entitlement;
    private final ReferenceDataService referenceData;
    private final MarketingEventPublisher events;
    private final MarketingMapper mapper;
    private final MarketingProperties properties;
    private final Clock clock;

    public CampaignService(CampaignRepository campaigns, CampaignRecipientRepository recipients,
            RefPatientRepository patients, AudienceQueryRepository audiences, MarketingViewRepository views,
            SegmentService segments, PromotionService promotions, MarketingEntitlementService entitlement,
            ReferenceDataService referenceData, MarketingEventPublisher events, MarketingMapper mapper,
            MarketingProperties properties, Clock clock) {
        this.campaigns = campaigns;
        this.recipients = recipients;
        this.patients = patients;
        this.audiences = audiences;
        this.views = views;
        this.segments = segments;
        this.promotions = promotions;
        this.entitlement = entitlement;
        this.referenceData = referenceData;
        this.events = events;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** Crea una campana en borrador. */
    @Transactional
    public CampaignResponse create(RequestContext context, CreateCampaignRequest request) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);
        referenceData.requireOrganization(organizationId);

        AudienceSegment segment = request.segmentId() == null
                ? null
                : segments.require(organizationId, request.segmentId());
        Promotion promotion = request.promotionId() == null
                ? null
                : promotions.require(organizationId, request.promotionId());

        Instant startsAt = DateTimes.toInstant(request.startsAt());
        Instant endsAt = DateTimes.toInstant(request.endsAt());
        if (endsAt != null && endsAt.isBefore(startsAt)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "La fecha de fin no puede ser anterior a la de inicio");
        }

        Campaign campaign = campaigns.save(Campaign.draft(organizationId, request.name(), request.type(),
                request.objective(), request.channel(), request.messageTemplateId(), segment, promotion,
                startsAt, endsAt, request.budgetBob(), context.userId()));
        return mapper.toResponse(campaign);
    }

    /** Devuelve una campana del tenant. */
    @Transactional(readOnly = true)
    public CampaignResponse get(RequestContext context, UUID campaignId) {
        entitlement.requireEntitled(context.organizationId());
        return mapper.toResponse(require(context.organizationId(), campaignId));
    }

    /** Lista las campanas del tenant; con {@code status} filtra por estado. */
    @Transactional(readOnly = true)
    public Page<CampaignSummaryResponse> list(RequestContext context, CampaignStatus status, Pageable pageable) {
        entitlement.requireEntitled(context.organizationId());
        Page<Campaign> page = status == null
                ? campaigns.findByOrganizationPublicId(context.organizationId(), pageable)
                : campaigns.findByOrganizationPublicIdAndStatus(context.organizationId(), status, pageable);
        return page.map(mapper::toSummary);
    }

    /**
     * Materializa la audiencia: congela quien recibira la campana y quien queda fuera.
     *
     * <p>Se congela en vez de reevaluarse en cada envio porque seis meses despues hay que poder
     * responder a quien se le mando y por que quedo fuera quien quedo fuera. Un criterio reevaluado
     * daria una respuesta distinta cada vez que se preguntara.
     *
     * <p>Los excluidos por opt-out se guardan como filas con su motivo. Un excluido que simplemente
     * no aparece no prueba que la exclusion se haya evaluado.
     */
    @Transactional
    public MaterializeAudienceResponse materializeAudience(RequestContext context, UUID campaignId) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);
        Campaign campaign = require(organizationId, campaignId);
        campaign.requireAudienceRefreshAllowed();

        if (campaign.getAudienceSegment() == null) {
            throw new BusinessRuleException(ErrorCode.CAMPAIGN_WITHOUT_AUDIENCE,
                    "La campana no tiene segmento asociado: no hay criterio con el que elegir destinatarios");
        }

        int maxSize = properties.audience().maxSize();
        LocalDate today = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC);
        List<AudienceCandidate> candidates = audiences.findCandidates(organizationId,
                campaign.getAudienceSegment().getCriteria(), today, maxSize);

        recipients.deleteByCampaignId(campaign.getId());

        List<CampaignRecipient> materialized = new ArrayList<>(candidates.size());
        long optedOut = 0;
        for (AudienceCandidate candidate : candidates) {
            if (candidate.marketingOptOut()) {
                materialized.add(CampaignRecipient.excludedByOptOut(campaign, candidate.patientPublicId(),
                        OPT_OUT_REASON));
                optedOut++;
            } else {
                materialized.add(CampaignRecipient.selected(campaign, candidate.patientPublicId()));
            }
        }
        recipients.saveAll(materialized);

        long reachable = candidates.size() - optedOut;
        campaign.recordAudience(reachable);
        campaign.getAudienceSegment().recordEvaluation(reachable, clock.instant());

        boolean truncated = candidates.size() == maxSize;
        if (truncated) {
            log.warn("La audiencia de la campana {} se corto en el tope de {} destinatarios", campaignId, maxSize);
        }
        return new MaterializeAudienceResponse(campaignId, candidates.size(), reachable, optedOut, truncated,
                DateTimes.toOffset(clock.instant()));
    }

    /**
     * Lanza la campana y publica {@code marketing.campana} en la misma transaccion.
     *
     * <p>Que el cambio de estado y el evento compartan transaccion es el motivo de que exista el
     * outbox: si fueran dos escrituras separadas, una caida entre ambas dejaria una campana
     * EN_EJECUCION de la que ningun consumidor se enteraria.
     */
    @Transactional
    public CampaignResponse launch(RequestContext context, UUID campaignId) {
        entitlement.requireEntitled(context.organizationId());
        Campaign campaign = require(context.organizationId(), campaignId);
        campaign.launch();
        events.publishCampaign(campaign, "LANZADA");
        log.info("Campana {} lanzada con {} destinatarios", campaignId, campaign.getTotalRecipients());
        return mapper.toResponse(campaign);
    }

    /** Programa la campana para su fecha de inicio. */
    @Transactional
    public CampaignResponse schedule(RequestContext context, UUID campaignId) {
        entitlement.requireEntitled(context.organizationId());
        Campaign campaign = require(context.organizationId(), campaignId);
        campaign.schedule();
        return mapper.toResponse(campaign);
    }

    /** Pausa la campana y avisa por evento para que ms-notificaciones detenga la cola. */
    @Transactional
    public CampaignResponse pause(RequestContext context, UUID campaignId) {
        entitlement.requireEntitled(context.organizationId());
        Campaign campaign = require(context.organizationId(), campaignId);
        campaign.pause();
        events.publishCampaign(campaign, "PAUSADA");
        return mapper.toResponse(campaign);
    }

    /** Cierra la campana. */
    @Transactional
    public CampaignResponse finish(RequestContext context, UUID campaignId) {
        entitlement.requireEntitled(context.organizationId());
        Campaign campaign = require(context.organizationId(), campaignId);
        campaign.finish();
        events.publishCampaign(campaign, "FINALIZADA");
        return mapper.toResponse(campaign);
    }

    /** Cancela la campana. */
    @Transactional
    public CampaignResponse cancel(RequestContext context, UUID campaignId) {
        entitlement.requireEntitled(context.organizationId());
        Campaign campaign = require(context.organizationId(), campaignId);
        campaign.cancel();
        events.publishCampaign(campaign, "CANCELADA");
        return mapper.toResponse(campaign);
    }

    /** Lista los destinatarios materializados; con {@code status} filtra por resultado. */
    @Transactional(readOnly = true)
    public Page<CampaignRecipientResponse> listRecipients(RequestContext context, UUID campaignId,
            RecipientStatus status, Pageable pageable) {
        entitlement.requireEntitled(context.organizationId());
        Campaign campaign = require(context.organizationId(), campaignId);

        Page<CampaignRecipient> page = status == null
                ? recipients.findByCampaignId(campaign.getId(), pageable)
                : recipients.findByCampaignIdAndStatus(campaign.getId(), status, pageable);

        Map<UUID, String> names = namesOf(page.getContent());
        return page.map(recipient -> mapper.toResponse(recipient, names.get(recipient.getPatientPublicId())));
    }

    /** Rendimiento de la campana, con las tasas calculadas por la vista de la base. */
    @Transactional(readOnly = true)
    public CampaignPerformanceResponse performance(RequestContext context, UUID campaignId) {
        entitlement.requireEntitled(context.organizationId());
        Campaign campaign = require(context.organizationId(), campaignId);
        Optional<CampaignPerformanceRow> row = views.campaignPerformance(campaign.getPublicId());

        return new CampaignPerformanceResponse(
                campaign.getPublicId(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getTotalRecipients(),
                campaign.getTotalSent(),
                campaign.getTotalRead(),
                campaign.getTotalConverted(),
                row.map(CampaignPerformanceRow::sendRatePct).orElse(BigDecimal.ZERO),
                row.map(CampaignPerformanceRow::readRatePct).orElse(BigDecimal.ZERO),
                row.map(CampaignPerformanceRow::conversionRatePct).orElse(BigDecimal.ZERO),
                row.map(CampaignPerformanceRow::excludedOptOut)
                        .orElseGet(() -> recipients.countByCampaignIdAndStatus(campaign.getId(),
                                RecipientStatus.EXCLUDED_OPT_OUT)));
    }

    /** Resuelve una campana del tenant o falla con 404. */
    @Transactional(readOnly = true)
    public Campaign require(UUID organizationId, UUID campaignId) {
        return campaigns.findByPublicIdAndOrganizationPublicId(campaignId, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND,
                        "La campana '" + campaignId + "' no existe en esta organizacion"));
    }

    /** Resuelve los nombres de una pagina de destinatarios en una sola consulta, no una por fila. */
    private Map<UUID, String> namesOf(List<CampaignRecipient> page) {
        List<UUID> ids = page.stream().map(CampaignRecipient::getPatientPublicId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return patients.findByPublicIdIn(ids).stream()
                .collect(Collectors.toMap(RefPatient::getPublicId, RefPatient::getFullName,
                        (first, second) -> first, () -> new java.util.HashMap<>()));
    }
}
