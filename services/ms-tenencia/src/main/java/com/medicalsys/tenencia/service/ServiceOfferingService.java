package com.medicalsys.tenencia.service;

import com.medicalsys.tenencia.common.error.ConflictException;
import com.medicalsys.tenencia.common.error.ErrorCode;
import com.medicalsys.tenencia.common.error.NotFoundException;
import com.medicalsys.tenencia.common.tenant.RequestContext;
import com.medicalsys.tenencia.domain.model.Organization;
import com.medicalsys.tenencia.domain.model.ServiceOffering;
import com.medicalsys.tenencia.mapper.TenancyMapper;
import com.medicalsys.tenencia.repository.ServiceOfferingRepository;
import com.medicalsys.tenencia.web.dto.BillableServiceResponse;
import com.medicalsys.tenencia.web.dto.ChangeServiceOfferingActiveRequest;
import com.medicalsys.tenencia.web.dto.CreateServiceOfferingRequest;
import com.medicalsys.tenencia.web.dto.SchedulableServiceResponse;
import com.medicalsys.tenencia.web.dto.ServiceOfferingResponse;
import com.medicalsys.tenencia.web.dto.UpdateServiceOfferingRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del catalogo de prestaciones.
 *
 * <p>Este agregado es el que consumen ms-agenda (duracion y recurso) y ms-facturacion (precio). El
 * precio se publica desde aqui y nunca viaja en el cuerpo de quien factura: si el emisor pudiera
 * enviarlo, podria elegir cuanto cobra.
 *
 * <p>Las prestaciones no se borran, se desactivan: una factura ya emitida conserva su detalle y no
 * debe quedar apuntando a una fila inexistente.
 */
@Service
public class ServiceOfferingService {

    private static final Logger log = LoggerFactory.getLogger(ServiceOfferingService.class);

    private final ServiceOfferingRepository offerings;
    private final OrganizationService organizations;
    private final TenancyMapper mapper;

    public ServiceOfferingService(ServiceOfferingRepository offerings, OrganizationService organizations,
            TenancyMapper mapper) {
        this.offerings = offerings;
        this.organizations = organizations;
        this.mapper = mapper;
    }

    /**
     * Publica una prestacion en el catalogo del tenant.
     *
     * @throws ConflictException si el codigo ya existe en la organizacion
     */
    @Transactional
    public ServiceOfferingResponse create(RequestContext context, CreateServiceOfferingRequest request) {
        UUID organizationId = context.organizationId();
        Organization organization = organizations.requireOperational(organizationId);

        String code = ServiceOffering.normalizeCode(request.code());
        if (offerings.existsByOrganizationPublicIdAndCode(organizationId, code)) {
            throw new ConflictException(ErrorCode.SERVICE_OFFERING_CODE_ALREADY_USED,
                    "Ya existe una prestacion con el codigo '" + code + "' en esta organizacion");
        }

        ServiceOffering offering = ServiceOffering.publish(organization, code, request.name(), request.type(),
                request.durationMinutes(), request.basePriceBob());
        offering.updateDetails(request.name(), request.description(), request.type(), request.specialtyCode(),
                request.durationMinutes(), request.basePriceBob(), request.requiredResourceType(),
                request.taxProductCode(), request.taxUnitCode());
        offering.requireConsent(request.requiresConsent(), request.consentTemplatePublicId());
        offerings.save(offering);

        log.info("Prestacion publicada organizationId={} serviceOfferingId={} code={} precio={}", organizationId,
                offering.getPublicId(), code, offering.getBasePriceBob());
        return mapper.toResponse(offering);
    }

    /**
     * Lista el catalogo del tenant.
     *
     * @param active        filtra por vigencia; null devuelve activas e inactivas
     * @param specialtyCode filtra por especialidad de catalogs_db; null no filtra
     */
    @Transactional(readOnly = true)
    public Page<ServiceOfferingResponse> list(RequestContext context, Boolean active, String specialtyCode,
            Pageable pageable) {
        UUID organizationId = context.organizationId();
        Page<ServiceOffering> page;
        if (active != null && specialtyCode != null) {
            page = offerings.findByOrganizationPublicIdAndActiveAndSpecialtyCode(organizationId, active,
                    specialtyCode, pageable);
        } else if (active != null) {
            page = offerings.findByOrganizationPublicIdAndActive(organizationId, active, pageable);
        } else if (specialtyCode != null) {
            page = offerings.findByOrganizationPublicIdAndSpecialtyCode(organizationId, specialtyCode, pageable);
        } else {
            page = offerings.findByOrganizationPublicId(organizationId, pageable);
        }
        return page.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ServiceOfferingResponse get(RequestContext context, UUID serviceOfferingId) {
        return mapper.toResponse(require(context.organizationId(), serviceOfferingId));
    }

    /**
     * Proyeccion facturable consumida por ms-facturacion.
     *
     * <p>El aislamiento se aplica igual que en las rutas publicas: un servicio vecino tampoco puede
     * leer el precio de otra clinica.
     */
    @Transactional(readOnly = true)
    public BillableServiceResponse getBillable(UUID organizationId, UUID serviceOfferingId) {
        return mapper.toBillable(require(organizationId, serviceOfferingId));
    }

    /** Proyeccion agendable consumida por ms-agenda. */
    @Transactional(readOnly = true)
    public SchedulableServiceResponse getSchedulable(UUID organizationId, UUID serviceOfferingId) {
        return mapper.toSchedulable(require(organizationId, serviceOfferingId));
    }

    @Transactional
    public ServiceOfferingResponse update(RequestContext context, UUID serviceOfferingId,
            UpdateServiceOfferingRequest request) {
        ServiceOffering offering = require(context.organizationId(), serviceOfferingId);
        offering.updateDetails(request.name(), request.description(), request.type(), request.specialtyCode(),
                request.durationMinutes(), request.basePriceBob(), request.requiredResourceType(),
                request.taxProductCode(), request.taxUnitCode());
        offering.requireConsent(request.requiresConsent(), request.consentTemplatePublicId());
        log.info("Prestacion actualizada organizationId={} serviceOfferingId={} precio={}", context.organizationId(),
                serviceOfferingId, offering.getBasePriceBob());
        return mapper.toResponse(offering);
    }

    /** Activa o desactiva una prestacion; desactivar no borra la fila. */
    @Transactional
    public ServiceOfferingResponse changeActive(RequestContext context, UUID serviceOfferingId,
            ChangeServiceOfferingActiveRequest request) {
        ServiceOffering offering = require(context.organizationId(), serviceOfferingId);
        offering.changeActive(request.active());
        log.info("Vigencia de prestacion actualizada organizationId={} serviceOfferingId={} active={}",
                context.organizationId(), serviceOfferingId, request.active());
        return mapper.toResponse(offering);
    }

    private ServiceOffering require(UUID organizationId, UUID serviceOfferingId) {
        return offerings.findByOrganizationPublicIdAndPublicId(organizationId, serviceOfferingId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SERVICE_OFFERING_NOT_FOUND,
                        "La prestacion '" + serviceOfferingId + "' no existe en esta organizacion"));
    }
}
