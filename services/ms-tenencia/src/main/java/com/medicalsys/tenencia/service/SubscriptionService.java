package com.medicalsys.tenencia.service;

import com.medicalsys.tenencia.common.error.BusinessRuleException;
import com.medicalsys.tenencia.common.error.ConflictException;
import com.medicalsys.tenencia.common.error.ErrorCode;
import com.medicalsys.tenencia.common.error.NotFoundException;
import com.medicalsys.tenencia.common.tenant.RequestContext;
import com.medicalsys.tenencia.config.TenancyProperties;
import com.medicalsys.tenencia.domain.enums.SubscriptionStatus;
import com.medicalsys.tenencia.domain.model.Organization;
import com.medicalsys.tenencia.domain.model.Subscription;
import com.medicalsys.tenencia.domain.model.SubscriptionPlan;
import com.medicalsys.tenencia.mapper.TenancyMapper;
import com.medicalsys.tenencia.repository.SubscriptionPlanRepository;
import com.medicalsys.tenencia.repository.SubscriptionRepository;
import com.medicalsys.tenencia.web.dto.ChangeSubscriptionStatusRequest;
import com.medicalsys.tenencia.web.dto.CreateSubscriptionRequest;
import com.medicalsys.tenencia.web.dto.SubscriptionPlanResponse;
import com.medicalsys.tenencia.web.dto.SubscriptionResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del contrato entre una organizacion y su plan.
 *
 * <p>Una organizacion tiene como mucho una suscripcion no cancelada. Contratar un plan nuevo con
 * otro ya vigente es un 409: primero se cancela el anterior, para que el limite aplicado sea
 * siempre inequivoco.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final TenancyProperties properties;
    private final TenancyMapper mapper;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptions, SubscriptionPlanRepository plans,
            TenancyProperties properties, TenancyMapper mapper, Clock clock) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Catalogo de planes activos de la plataforma, del mas barato al mas caro. */
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> listActivePlans() {
        return plans.findByActiveTrueOrderByMonthlyPriceBobAsc().stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Abre la suscripcion inicial de una organizacion recien creada.
     *
     * <p>Nace en TRIAL con la duracion configurada. Se llama desde el alta del tenant, dentro de la
     * misma transaccion: una organizacion sin contrato no podria evaluar ningun limite de plan.
     */
    @Transactional
    public SubscriptionPlan openInitialSubscription(Organization organization, String planCode, LocalDate today) {
        SubscriptionPlan plan = requireActivePlan(planCode);
        LocalDate trialEnd = today.plusDays(properties.trialPeriod().toDays());
        Subscription subscription = Subscription.open(organization, plan, null, today, trialEnd, null);
        subscriptions.save(subscription);
        return plan;
    }

    /** Suscripcion vigente del tenant del contexto. */
    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrent(RequestContext context) {
        LocalDate today = LocalDate.now(clock);
        return subscriptions.findCurrentByOrganization(context.organizationId())
                .map(subscription -> mapper.toResponse(subscription, today))
                .orElseThrow(() -> new NotFoundException(ErrorCode.SUBSCRIPTION_NOT_FOUND,
                        "La organizacion no tiene una suscripcion vigente"));
    }

    /** Historial de contratos del tenant, del mas reciente al mas antiguo. */
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listHistory(RequestContext context) {
        LocalDate today = LocalDate.now(clock);
        return subscriptions.findHistory(context.organizationId()).stream()
                .map(subscription -> mapper.toResponse(subscription, today))
                .toList();
    }

    /**
     * Contrata un plan para el tenant.
     *
     * @throws ConflictException       si ya existe una suscripcion no cancelada
     * @throws BusinessRuleException   si el plan no existe, esta inactivo o el periodo es invalido
     */
    @Transactional
    public SubscriptionResponse subscribe(RequestContext context, Organization organization,
            CreateSubscriptionRequest request) {
        if (subscriptions.findCurrentByOrganization(context.organizationId()).isPresent()) {
            throw new ConflictException(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE,
                    "La organizacion ya tiene una suscripcion vigente; cancelela antes de contratar otra");
        }
        SubscriptionPlan plan = requireActivePlan(request.planCode());
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = request.startDate() == null ? today : request.startDate();
        LocalDate endDate = request.endDate();
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessRuleException(ErrorCode.INVALID_SUBSCRIPTION_PERIOD,
                    "La fecha de fin no puede ser anterior a la de inicio");
        }

        Subscription subscription = Subscription.open(organization, plan, request.billingCycle(), startDate, endDate,
                request.agreedPriceBob());
        subscriptions.save(subscription);
        log.info("Suscripcion contratada organizationId={} plan={} ciclo={}", organization.getPublicId(),
                plan.getCode(), subscription.getBillingCycle());
        return mapper.toResponse(subscription, today);
    }

    /** Cambia el estado del contrato vigente del tenant. */
    @Transactional
    public SubscriptionResponse changeStatus(RequestContext context, ChangeSubscriptionStatusRequest request) {
        LocalDate today = LocalDate.now(clock);
        Subscription subscription = subscriptions.findCurrentByOrganization(context.organizationId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.SUBSCRIPTION_NOT_FOUND,
                        "La organizacion no tiene una suscripcion vigente"));
        subscription.changeStatus(request.status(), today);
        log.info("Estado de suscripcion actualizado organizationId={} status={}", context.organizationId(),
                request.status());
        return mapper.toResponse(subscription, today);
    }

    /** Indica si el tenant tiene un contrato que habilita el uso del producto hoy. */
    @Transactional(readOnly = true)
    public boolean hasEffectiveSubscription(UUID organizationId) {
        LocalDate today = LocalDate.now(clock);
        return subscriptions.findCurrentByOrganization(organizationId)
                .filter(subscription -> subscription.isEffectiveOn(today))
                .isPresent();
    }

    /**
     * Plan que rige los limites del tenant.
     *
     * <p>Vacio si no hay contrato vigente; en ese caso no se aplica ningun limite de plan y la
     * decision queda en el estado de la organizacion.
     */
    @Transactional(readOnly = true)
    public Optional<SubscriptionPlan> currentPlan(UUID organizationId) {
        return subscriptions.findCurrentByOrganization(organizationId).map(Subscription::getPlan);
    }

    /** Indica si el tenant conserva un contrato en estado TRIAL. */
    @Transactional(readOnly = true)
    public boolean isOnTrial(UUID organizationId) {
        return subscriptions.existsByOrganizationPublicIdAndStatus(organizationId, SubscriptionStatus.TRIAL);
    }

    private SubscriptionPlan requireActivePlan(String planCode) {
        String code = planCode.trim().toUpperCase(Locale.ROOT);
        SubscriptionPlan plan = plans.findByCode(code)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SUBSCRIPTION_PLAN_NOT_FOUND,
                        "El plan '" + code + "' no existe en el catalogo"));
        if (!plan.isActive()) {
            throw new BusinessRuleException(ErrorCode.SUBSCRIPTION_PLAN_NOT_ACTIVE,
                    "El plan '" + code + "' esta desactivado y no admite contrataciones nuevas");
        }
        return plan;
    }
}
