package com.medicalsys.marketing.service;

import com.medicalsys.marketing.common.DateTimes;
import com.medicalsys.marketing.common.error.BusinessRuleException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.common.error.NotFoundException;
import com.medicalsys.marketing.common.tenant.RequestContext;
import com.medicalsys.marketing.domain.enums.AdvertisementPlacement;
import com.medicalsys.marketing.domain.enums.AdvertisementStatus;
import com.medicalsys.marketing.domain.model.Advertisement;
import com.medicalsys.marketing.mapper.MarketingMapper;
import com.medicalsys.marketing.privacy.ClinicalDataGuard;
import com.medicalsys.marketing.repository.AdvertisementRepository;
import com.medicalsys.marketing.web.dto.AdvertisementResponse;
import com.medicalsys.marketing.web.dto.CreateAdvertisementRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del anuncio del portal del paciente.
 *
 * <p>A diferencia de la campana, el anuncio es pasivo: no busca a nadie, se muestra a quien entra.
 * Por eso no tiene audiencia materializada ni evento saliente, y su publico objetivo es un filtro
 * ligero. Ese filtro pasa igualmente por {@link ClinicalDataGuard}: un anuncio dirigido por
 * patologia seria la misma filtracion que un segmento clinico, solo que con otra ropa.
 */
@Service
public class AdvertisementService {

    /** Tope de anuncios que el portal recibe de una vez: es una franja, no un catalogo. */
    private static final int LIVE_LIMIT = 20;

    private final AdvertisementRepository advertisements;
    private final MarketingEntitlementService entitlement;
    private final ReferenceDataService referenceData;
    private final ClinicalDataGuard clinicalDataGuard;
    private final MarketingMapper mapper;
    private final Clock clock;

    public AdvertisementService(AdvertisementRepository advertisements, MarketingEntitlementService entitlement,
            ReferenceDataService referenceData, ClinicalDataGuard clinicalDataGuard, MarketingMapper mapper,
            Clock clock) {
        this.advertisements = advertisements;
        this.entitlement = entitlement;
        this.referenceData = referenceData;
        this.clinicalDataGuard = clinicalDataGuard;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Crea un anuncio en borrador para el tenant. */
    @Transactional
    public AdvertisementResponse create(RequestContext context, CreateAdvertisementRequest request) {
        UUID organizationId = context.organizationId();
        entitlement.requireEntitled(organizationId);
        referenceData.requireOrganization(organizationId);

        // El publico objetivo de un anuncio se somete a la misma frontera que el criterio de un segmento.
        clinicalDataGuard.assertEventPayloadAllowed(request.targetAudience(), "publico objetivo del anuncio");

        Instant validFrom = DateTimes.toInstant(request.validFrom());
        Instant validUntil = DateTimes.toInstant(request.validUntil());
        if (!validUntil.isAfter(validFrom)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "La vigencia del anuncio debe terminar despues de empezar");
        }
        if (request.serviceOfferingId() != null) {
            referenceData.findOrHydrateServiceOffering(organizationId, request.serviceOfferingId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                            "La prestacion '" + request.serviceOfferingId() + "' no existe en esta organizacion"));
        }

        Advertisement advertisement = advertisements.save(Advertisement.draft(organizationId, request.title(),
                request.content(), request.imageDocumentId(), request.targetUrl(), request.serviceOfferingId(),
                request.placement(), request.priority(), request.targetAudience(), validFrom, validUntil,
                context.userId()));
        return mapper.toResponse(advertisement);
    }

    /** Devuelve un anuncio visible para el tenant: el suyo o uno institucional. */
    @Transactional(readOnly = true)
    public AdvertisementResponse get(RequestContext context, UUID advertisementId) {
        entitlement.requireEntitled(context.organizationId());
        return mapper.toResponse(requireVisible(context.organizationId(), advertisementId));
    }

    /** Lista los anuncios visibles para el tenant. */
    @Transactional(readOnly = true)
    public Page<AdvertisementResponse> list(RequestContext context, Pageable pageable) {
        entitlement.requireEntitled(context.organizationId());
        return advertisements.findAllVisible(context.organizationId(), pageable).map(mapper::toResponse);
    }

    /**
     * Anuncios que el portal debe mostrar ahora, ordenados por prioridad.
     *
     * <p>Es la unica ruta pensada para el rol PATIENT: devuelve lo que se publica, sin metricas de
     * negocio ni datos de otros pacientes.
     */
    @Transactional(readOnly = true)
    public List<AdvertisementResponse> live(RequestContext context, AdvertisementPlacement placement) {
        entitlement.requireEntitled(context.organizationId());
        AdvertisementPlacement resolved = placement == null
                ? AdvertisementPlacement.PATIENT_PORTAL
                : placement;
        return advertisements.findLive(context.organizationId(), resolved, AdvertisementStatus.PUBLISHED,
                        clock.instant(), PageRequest.of(0, LIVE_LIMIT))
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Publica el anuncio. */
    @Transactional
    public AdvertisementResponse publish(RequestContext context, UUID advertisementId) {
        entitlement.requireEntitled(context.organizationId());
        Advertisement advertisement = requireOwned(context.organizationId(), advertisementId);
        advertisement.publish();
        return mapper.toResponse(advertisement);
    }

    /** Retira temporalmente el anuncio. */
    @Transactional
    public AdvertisementResponse pause(RequestContext context, UUID advertisementId) {
        entitlement.requireEntitled(context.organizationId());
        Advertisement advertisement = requireOwned(context.organizationId(), advertisementId);
        advertisement.pause();
        return mapper.toResponse(advertisement);
    }

    /** Cierra el anuncio de forma definitiva. */
    @Transactional
    public AdvertisementResponse finish(RequestContext context, UUID advertisementId) {
        entitlement.requireEntitled(context.organizationId());
        Advertisement advertisement = requireOwned(context.organizationId(), advertisementId);
        advertisement.finish();
        return mapper.toResponse(advertisement);
    }

    /** Suma una impresion. La cuenta la lleva el servidor, no el cliente. */
    @Transactional
    public AdvertisementResponse registerImpression(RequestContext context, UUID advertisementId) {
        entitlement.requireEntitled(context.organizationId());
        Advertisement advertisement = requireVisible(context.organizationId(), advertisementId);
        advertisement.registerImpression(clock.instant());
        return mapper.toResponse(advertisement);
    }

    /** Suma un clic. */
    @Transactional
    public AdvertisementResponse registerClick(RequestContext context, UUID advertisementId) {
        entitlement.requireEntitled(context.organizationId());
        Advertisement advertisement = requireVisible(context.organizationId(), advertisementId);
        advertisement.registerClick(clock.instant());
        return mapper.toResponse(advertisement);
    }

    /** Anuncio que el tenant puede ver: el suyo o uno de la plataforma. */
    private Advertisement requireVisible(UUID organizationId, UUID advertisementId) {
        return advertisements.findVisible(advertisementId, organizationId)
                .orElseThrow(() -> notFound(advertisementId));
    }

    /**
     * Anuncio que el tenant puede editar: solo el suyo.
     *
     * <p>Un anuncio institucional se ve pero no se toca; devolver 404 y no 403 evita confirmar su
     * existencia a quien no puede administrarlo.
     */
    private Advertisement requireOwned(UUID organizationId, UUID advertisementId) {
        return advertisements.findByPublicIdAndOrganizationPublicId(advertisementId, organizationId)
                .orElseThrow(() -> notFound(advertisementId));
    }

    private NotFoundException notFound(UUID advertisementId) {
        return new NotFoundException(ErrorCode.ADVERTISEMENT_NOT_FOUND,
                "El anuncio '" + advertisementId + "' no existe o no es administrable por esta organizacion");
    }
}
