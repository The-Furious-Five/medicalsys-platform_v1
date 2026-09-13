package com.medicalsys.marketing.service;

import tools.jackson.databind.ObjectMapper;
import com.medicalsys.marketing.common.DateTimes;
import com.medicalsys.marketing.common.error.BusinessRuleException;
import com.medicalsys.marketing.common.error.ConflictException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.common.error.NotFoundException;
import com.medicalsys.marketing.common.tenant.RequestContext;
import com.medicalsys.marketing.config.MarketingProperties;
import com.medicalsys.marketing.domain.model.AudienceSegment;
import com.medicalsys.marketing.mapper.MarketingMapper;
import com.medicalsys.marketing.privacy.ClinicalDataGuard;
import com.medicalsys.marketing.privacy.SegmentCriteria;
import com.medicalsys.marketing.repository.AudienceQueryRepository;
import com.medicalsys.marketing.repository.AudienceQueryRepository.AudienceCandidate;
import com.medicalsys.marketing.repository.AudienceSegmentRepository;
import com.medicalsys.marketing.repository.CampaignRepository;
import com.medicalsys.marketing.web.dto.AudienceMemberResponse;
import com.medicalsys.marketing.web.dto.CreateSegmentRequest;
import com.medicalsys.marketing.web.dto.SegmentPreviewResponse;
import com.medicalsys.marketing.web.dto.SegmentResponse;
import com.medicalsys.marketing.web.dto.UpdateSegmentRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de la segmentacion de audiencias.
 *
 * <p>Todo criterio que entra al sistema pasa por aqui y, dentro, por dos filtros consecutivos:
 * {@link ClinicalDataGuard} sobre el JSON crudo y la vinculacion a {@link SegmentCriteria}. Ningun
 * otro camino escribe en {@code audience_segment}, de modo que no existe forma de persistir un
 * criterio que no haya sido inspeccionado.
 */
@Service
public class SegmentService {

    /** Tamano de la muestra que devuelve la vista previa: suficiente para revisar, no para exportar. */
    private static final int PREVIEW_SAMPLE_SIZE = 20;

    private final AudienceSegmentRepository segments;
    private final AudienceQueryRepository audiences;
    private final CampaignRepository campaigns;
    private final MarketingEntitlementService entitlement;
    private final ReferenceDataService referenceData;
    private final ClinicalDataGuard clinicalDataGuard;
    private final MarketingMapper mapper;
    private final ObjectMapper objectMapper;
    private final MarketingProperties properties;
    private final Clock clock;

    public SegmentService(AudienceSegmentRepository segments, AudienceQueryRepository audiences,
            CampaignRepository campaigns, MarketingEntitlementService entitlement,
            ReferenceDataService referenceData, ClinicalDataGuard clinicalDataGuard, MarketingMapper mapper,
            ObjectMapper objectMapper, MarketingProperties properties, Clock clock) {
        this.segments = segments;
        this.audiences = audiences;
        this.campaigns = campaigns;
        this.entitlement = entitlement;
        this.referenceData = referenceData;
        this.clinicalDataGuard = clinicalDataGuard;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** Crea un segmento sobre un criterio validado. */
    @Transactional
    public SegmentResponse create(RequestContext context, CreateSegmentRequest request) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);
        referenceData.requireOrganization(organizationId);

        SegmentCriteria criteria = bindCriteria(request.criteria());

        if (segments.existsByOrganizationPublicIdAndName(organizationId, request.name())) {
            throw new ConflictException(ErrorCode.SEGMENT_ALREADY_EXISTS,
                    "Ya existe un segmento llamado '" + request.name() + "' en esta organizacion");
        }

        AudienceSegment segment = segments.save(AudienceSegment.define(organizationId, request.name(),
                request.description(), criteria, context.userId()));

        // Se evalua al crear para que quien lo define vea de inmediato a cuanta gente alcanza.
        segment.recordEvaluation(audiences.countReachable(organizationId, criteria, today()), clock.instant());
        return mapper.toResponse(segment);
    }

    /** Modifica la descripcion o el criterio de un segmento. */
    @Transactional
    public SegmentResponse update(RequestContext context, UUID segmentId, UpdateSegmentRequest request) {
        entitlement.requireEntitled(context.organizationId());
        AudienceSegment segment = require(context.organizationId(), segmentId);

        SegmentCriteria criteria = request.criteria() == null ? null : bindCriteria(request.criteria());
        segment.redefine(request.description(), criteria);

        if (criteria != null) {
            segment.recordEvaluation(audiences.countReachable(context.organizationId(), criteria, today()),
                    clock.instant());
        }
        return mapper.toResponse(segment);
    }

    /** Devuelve un segmento del tenant. */
    @Transactional(readOnly = true)
    public SegmentResponse get(RequestContext context, UUID segmentId) {
        entitlement.requireEntitled(context.organizationId());
        return mapper.toResponse(require(context.organizationId(), segmentId));
    }

    /** Lista los segmentos del tenant; con {@code active} filtra por vigencia. */
    @Transactional(readOnly = true)
    public Page<SegmentResponse> list(RequestContext context, Boolean active, Pageable pageable) {
        entitlement.requireEntitled(context.organizationId());
        Page<AudienceSegment> page = active == null
                ? segments.findByOrganizationPublicId(context.organizationId(), pageable)
                : segments.findByOrganizationPublicIdAndActive(context.organizationId(), active, pageable);
        return page.map(mapper::toResponse);
    }

    /**
     * Evalua un segmento guardado y devuelve su tamano con una muestra.
     *
     * <p>Reevaluar tiene sentido porque la audiencia se mueve sola: cada visita y cada factura que
     * llega por evento cambia {@code patient_activity}, y con ella el resultado de un criterio de
     * reactivacion.
     */
    @Transactional
    public SegmentPreviewResponse evaluate(RequestContext context, UUID segmentId) {
        entitlement.requireEntitled(context.organizationId());
        AudienceSegment segment = require(context.organizationId(), segmentId);
        SegmentPreviewResponse preview = preview(context.organizationId(), segment.getCriteria());
        segment.recordEvaluation(preview.reachable(), clock.instant());
        return preview;
    }

    /**
     * Evalua un criterio sin guardarlo.
     *
     * <p>Es la operacion que evita el error caro: ver el tamano antes de crear la campana. Una
     * campana lanzada sobre un criterio equivocado ya no se recoge.
     */
    @Transactional(readOnly = true)
    public SegmentPreviewResponse previewRaw(RequestContext context, Map<String, Object> rawCriteria) {
        entitlement.requireEntitled(context.organizationId());
        return preview(context.organizationId(), bindCriteria(rawCriteria));
    }

    /** Desactiva un segmento; no se borra si alguna campana lo referencia. */
    @Transactional
    public SegmentResponse deactivate(RequestContext context, UUID segmentId) {
        entitlement.requireEntitled(context.organizationId());
        AudienceSegment segment = require(context.organizationId(), segmentId);
        segment.deactivate();
        return mapper.toResponse(segment);
    }

    /**
     * Elimina un segmento.
     *
     * <p>Solo si ninguna campana lo usa: borrarlo con campanas asociadas dejaria sin explicacion la
     * audiencia de una campana ya ejecutada, que es justo lo que hay que poder auditar despues.
     */
    @Transactional
    public void delete(RequestContext context, UUID segmentId) {
        entitlement.requireEntitled(context.organizationId());
        AudienceSegment segment = require(context.organizationId(), segmentId);
        long usedBy = campaigns.countByAudienceSegmentId(segment.getId());
        if (usedBy > 0) {
            throw new ConflictException(ErrorCode.DATA_INTEGRITY_CONFLICT,
                    "El segmento esta referenciado por " + usedBy + " campana(s) y no puede eliminarse. "
                            + "Desactivelo en su lugar");
        }
        segments.delete(segment);
    }

    /** Resuelve un segmento del tenant o falla con 404. */
    @Transactional(readOnly = true)
    public AudienceSegment require(UUID organizationId, UUID segmentId) {
        return segments.findByPublicIdAndOrganizationPublicId(segmentId, organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SEGMENT_NOT_FOUND,
                        "El segmento '" + segmentId + "' no existe en esta organizacion"));
    }

    /** Evalua un criterio y arma la vista previa con su muestra. */
    private SegmentPreviewResponse preview(UUID organizationId, SegmentCriteria criteria) {
        LocalDate today = today();
        List<AudienceCandidate> candidates = audiences.findCandidates(organizationId, criteria, today,
                properties.audience().maxSize());

        long optedOut = candidates.stream().filter(AudienceCandidate::marketingOptOut).count();
        List<AudienceMemberResponse> sample = candidates.stream()
                .limit(PREVIEW_SAMPLE_SIZE)
                .map(candidate -> new AudienceMemberResponse(
                        candidate.patientPublicId(),
                        candidate.fullName(),
                        candidate.marketingOptOut() ? "EXCLUDED_OPT_OUT" : "SELECTED",
                        candidate.marketingOptOut() ? "El paciente pidio no recibir comunicaciones comerciales"
                                : null))
                .toList();

        return new SegmentPreviewResponse(candidates.size(), candidates.size() - optedOut, optedOut, sample,
                DateTimes.toOffset(clock.instant()));
    }

    /**
     * Valida el criterio crudo y lo vincula al tipo.
     *
     * <p>El orden es lo importante y no es negociable: primero el guardian sobre el mapa sin tipar,
     * despues la vinculacion. Al reves, Jackson descartaria en silencio un campo desconocido -- un
     * {@code diagnosticoCie10}, por ejemplo -- y el intento quedaria sin registro ni respuesta.
     */
    private SegmentCriteria bindCriteria(Map<String, Object> rawCriteria) {
        clinicalDataGuard.assertSegmentCriteriaAllowed(rawCriteria);

        SegmentCriteria criteria;
        try {
            criteria = objectMapper.convertValue(rawCriteria, SegmentCriteria.class);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(ErrorCode.SEGMENT_CRITERIA_INVALID,
                    "Un valor del criterio no tiene el tipo esperado: " + e.getMessage());
        }

        String error = criteria.validationError();
        if (error != null) {
            throw new BusinessRuleException(ErrorCode.SEGMENT_CRITERIA_INVALID, error);
        }
        return criteria;
    }

    /** Fecha de referencia para las edades, en UTC como el resto del servicio. */
    private LocalDate today() {
        return LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC);
    }
}
