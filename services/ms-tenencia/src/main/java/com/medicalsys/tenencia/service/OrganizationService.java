package com.medicalsys.tenencia.service;

import com.medicalsys.tenencia.common.error.BusinessRuleException;
import com.medicalsys.tenencia.common.error.ConflictException;
import com.medicalsys.tenencia.common.error.ErrorCode;
import com.medicalsys.tenencia.common.error.NotFoundException;
import com.medicalsys.tenencia.common.tenant.RequestContext;
import com.medicalsys.tenencia.config.TenancyProperties;
import com.medicalsys.tenencia.domain.enums.OrganizationStatus;
import com.medicalsys.tenencia.domain.model.Organization;
import com.medicalsys.tenencia.domain.model.SubscriptionPlan;
import com.medicalsys.tenencia.mapper.TenancyMapper;
import com.medicalsys.tenencia.repository.OrganizationRepository;
import com.medicalsys.tenencia.web.dto.ChangeOrganizationStatusRequest;
import com.medicalsys.tenencia.web.dto.CreateOrganizationRequest;
import com.medicalsys.tenencia.web.dto.OrganizationRef;
import com.medicalsys.tenencia.web.dto.OrganizationResponse;
import com.medicalsys.tenencia.web.dto.UpdateOrganizationRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del tenant.
 *
 * <p>Dar de alta una organizacion crea tambien su suscripcion inicial en periodo de prueba y abre
 * los contadores de cuota. Sin esos dos pasos, el tenant existiria pero ninguna regla de limite
 * podria evaluarse.
 *
 * <p>Toda lectura entra por el {@code organizationId} del contexto, que el gateway deriva del JWT.
 * Ninguna operacion acepta un identificador de tenant desde el cuerpo o la query.
 */
@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizations;
    private final SubscriptionService subscriptions;
    private final PlanUsageService planUsage;
    private final TenancyProperties properties;
    private final TenancyMapper mapper;
    private final Clock clock;

    public OrganizationService(OrganizationRepository organizations, SubscriptionService subscriptions,
            PlanUsageService planUsage, TenancyProperties properties, TenancyMapper mapper, Clock clock) {
        this.organizations = organizations;
        this.subscriptions = subscriptions;
        this.planUsage = planUsage;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Da de alta un tenant con su suscripcion de prueba y sus contadores de cuota.
     *
     * @throws ConflictException si el slug o el NIT ya pertenecen a otra organizacion
     */
    @Transactional
    public OrganizationResponse create(CreateOrganizationRequest request) {
        String code = Organization.normalizeCode(request.code());
        if (organizations.existsByCode(code)) {
            throw new ConflictException(ErrorCode.ORGANIZATION_CODE_ALREADY_USED,
                    "Ya existe una organizacion con el codigo '" + code + "'");
        }
        String nit = request.nit().trim();
        if (organizations.existsByNit(nit)) {
            throw new ConflictException(ErrorCode.ORGANIZATION_NIT_ALREADY_USED,
                    "Ya existe una organizacion registrada con ese NIT");
        }

        Organization organization = Organization.onboard(code, request.type(), request.legalName(),
                request.tradeName(), nit, request.contactEmail(),
                blankToDefault(request.timeZone(), properties.defaultTimeZone()),
                blankToDefault(request.currency(), properties.defaultCurrency()));
        organization.locateAt(request.city(), request.department());
        organizations.save(organization);

        LocalDate today = LocalDate.now(clock);
        SubscriptionPlan plan = subscriptions.openInitialSubscription(organization,
                blankToDefault(request.planCode(), properties.defaultPlanCode()), today);
        planUsage.openCounters(organization, today);

        log.info("Organizacion dada de alta code={} organizationId={} plan={}", code, organization.getPublicId(),
                plan.getCode());
        return mapper.toResponse(organization);
    }

    /** Devuelve la organizacion del contexto. */
    @Transactional(readOnly = true)
    public OrganizationResponse getCurrent(RequestContext context) {
        return mapper.toResponse(require(context.organizationId()));
    }

    /** Proyeccion minima para consumo interno de otro microservicio. */
    @Transactional(readOnly = true)
    public OrganizationRef getRef(UUID organizationId) {
        return mapper.toRef(require(organizationId));
    }

    /** Actualiza los datos administrativos del tenant del contexto. */
    @Transactional
    public OrganizationResponse update(RequestContext context, UpdateOrganizationRequest request) {
        Organization organization = require(context.organizationId());
        organization.updateProfile(request.legalName(), request.tradeName(), request.contactEmail(),
                request.phoneE164(), request.city(), request.department(),
                blankToDefault(request.timeZone(), organization.getTimeZone()), request.logoUrl(),
                request.tradeRegistryNumber());
        return mapper.toResponse(organization);
    }

    /**
     * Cambia el estado del tenant.
     *
     * <p>Activar exige una suscripcion vigente: una clinica sin contrato no debe poder operar.
     */
    @Transactional
    public OrganizationResponse changeStatus(RequestContext context, ChangeOrganizationStatusRequest request) {
        Organization organization = require(context.organizationId());
        OrganizationStatus target = request.status();
        if (target == OrganizationStatus.ACTIVE && !subscriptions.hasEffectiveSubscription(organization.getPublicId())) {
            throw new BusinessRuleException(ErrorCode.SUBSCRIPTION_NOT_ACTIVE,
                    "La organizacion no puede activarse sin una suscripcion vigente");
        }
        organization.changeStatus(target);
        log.info("Estado de organizacion actualizado organizationId={} status={}", organization.getPublicId(), target);
        return mapper.toResponse(organization);
    }

    /**
     * Carga el tenant o falla con 404.
     *
     * <p>Es el punto por el que pasan todos los servicios de este modulo: si el tenant del token no
     * existe en tenancy_db, ninguna operacion posterior tiene sentido.
     */
    @Transactional(readOnly = true)
    public Organization require(UUID organizationId) {
        return organizations.findByPublicId(organizationId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND,
                        "La organizacion '" + organizationId + "' no existe"));
    }

    /**
     * Carga el tenant y exige que este operativo.
     *
     * <p>Lo usan las altas: una organizacion suspendida conserva sus datos y puede consultarlos,
     * pero no registra sucursales ni prestaciones nuevas.
     */
    @Transactional(readOnly = true)
    public Organization requireOperational(UUID organizationId) {
        Organization organization = require(organizationId);
        if (!organization.isOperational()) {
            throw new BusinessRuleException(ErrorCode.ORGANIZATION_NOT_ACTIVE,
                    "La organizacion esta en estado " + organization.getStatus()
                            + " y no admite cambios de configuracion");
        }
        return organization;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
