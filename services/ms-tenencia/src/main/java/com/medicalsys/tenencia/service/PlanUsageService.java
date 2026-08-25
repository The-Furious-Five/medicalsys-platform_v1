package com.medicalsys.tenencia.service;

import com.medicalsys.tenencia.common.error.BusinessRuleException;
import com.medicalsys.tenencia.common.error.ErrorCode;
import com.medicalsys.tenencia.common.tenant.RequestContext;
import com.medicalsys.tenencia.config.TenancyProperties;
import com.medicalsys.tenencia.domain.enums.UsageMetric;
import com.medicalsys.tenencia.domain.model.Organization;
import com.medicalsys.tenencia.domain.model.PlanUsage;
import com.medicalsys.tenencia.domain.model.SubscriptionPlan;
import com.medicalsys.tenencia.mapper.TenancyMapper;
import com.medicalsys.tenencia.repository.PlanUsageRepository;
import com.medicalsys.tenencia.web.dto.PlanUsageResponse;
import com.medicalsys.tenencia.web.dto.RecordUsageRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuotas del plan materializadas por tenant.
 *
 * <p>Quien consume la cuota vive en otra base: los usuarios en identity_db y el almacenamiento en
 * documents_db. Como no hay FK entre bases, el limite solo puede aplicarse contrastando el valor
 * materializado de {@code plan_usage} contra el limite del plan contratado.
 *
 * <p>La reserva de cuota bloquea la fila del contador antes de decidir. Sin ese bloqueo, dos altas
 * simultaneas podrian leer el mismo valor, concluir ambas que queda una unidad libre y dejar al
 * tenant por encima de su limite.
 */
@Service
public class PlanUsageService {

    private static final Logger log = LoggerFactory.getLogger(PlanUsageService.class);

    private final PlanUsageRepository usages;
    private final SubscriptionService subscriptions;
    private final OrganizationService organizations;
    private final TenancyProperties properties;
    private final TenancyMapper mapper;
    private final Clock clock;

    /**
     * @param organizations se inyecta perezosamente porque el alta de una organizacion abre sus
     *                      contadores, de modo que ambos servicios se referencian mutuamente
     */
    public PlanUsageService(PlanUsageRepository usages, SubscriptionService subscriptions,
            @Lazy OrganizationService organizations, TenancyProperties properties, TenancyMapper mapper, Clock clock) {
        this.usages = usages;
        this.subscriptions = subscriptions;
        this.organizations = organizations;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Abre en cero los contadores de las tres metricas del diccionario.
     *
     * <p>Se llama al dar de alta el tenant: un contador ausente no puede distinguirse de un consumo
     * desconocido, asi que todas las metricas existen desde el primer dia.
     */
    @Transactional
    public void openCounters(Organization organization, LocalDate today) {
        for (UsageMetric metric : UsageMetric.values()) {
            usages.save(PlanUsage.start(organization, metric, today, null));
        }
    }

    /** Consumo de todas las metricas del tenant frente a los limites de su plan. */
    @Transactional(readOnly = true)
    public List<PlanUsageResponse> list(RequestContext context) {
        Optional<SubscriptionPlan> plan = subscriptions.currentPlan(context.organizationId());
        return usages.findByOrganizationPublicIdOrderByMetricAsc(context.organizationId()).stream()
                .map(usage -> mapper.toResponse(usage, limitFor(plan, usage.getMetric())))
                .toList();
    }

    /**
     * Registra el consumo reportado por el servicio propietario del recurso.
     *
     * <p>Acepta un incremento relativo o un recuento absoluto, nunca ambos: recibir los dos
     * significa que el emisor no sabe cual es el valor correcto.
     */
    @Transactional
    public PlanUsageResponse record(RequestContext context, RecordUsageRequest request) {
        if (request.delta() == null && request.value() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Debe indicarse 'delta' o 'value' para registrar el consumo");
        }
        if (request.delta() != null && request.value() != null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Debe indicarse 'delta' o 'value', pero no ambos");
        }

        LocalDate today = LocalDate.now(clock);
        PlanUsage usage = lockOrOpen(context.organizationId(), request.metric(), today);
        if (request.value() != null) {
            usage.set(request.value());
        } else {
            usage.add(request.delta());
        }

        Long limit = limitFor(subscriptions.currentPlan(context.organizationId()), request.metric());
        log.info("Consumo registrado organizationId={} metric={} valor={} limite={}", context.organizationId(),
                request.metric(), usage.getCurrentValue(), limit);
        return mapper.toResponse(usage, limit);
    }

    /**
     * Reserva cuota antes de crear el recurso que la consume.
     *
     * <p>Es la regla critica del agregado: si el plan limita la metrica y el consumo actual mas el
     * delta superan ese limite, la operacion se detiene con 422 y nada se persiste.
     *
     * @param organizationId tenant medido
     * @param metric         cuota a consumir
     * @param delta          unidades a reservar
     * @throws BusinessRuleException si el plan no admite el consumo solicitado
     */
    @Transactional
    public void reserve(UUID organizationId, UsageMetric metric, long delta) {
        LocalDate today = LocalDate.now(clock);
        PlanUsage usage = lockOrOpen(organizationId, metric, today);
        Long limit = limitFor(subscriptions.currentPlan(organizationId), metric);

        if (properties.enforcePlanLimits() && limit != null && usage.wouldExceed(delta, limit)) {
            throw new BusinessRuleException(ErrorCode.PLAN_LIMIT_REACHED,
                    "El plan contratado permite hasta " + limit + " " + metric
                            + " y la organizacion ya tiene " + usage.getCurrentValue());
        }
        usage.add(delta);
    }

    /** Devuelve cuota al eliminar o desactivar el recurso que la consumia. */
    @Transactional
    public void release(UUID organizationId, UsageMetric metric, long delta) {
        LocalDate today = LocalDate.now(clock);
        lockOrOpen(organizationId, metric, today).add(-delta);
    }

    /**
     * Fija el valor materializado de una metrica tras un recuento completo.
     *
     * <p>Sirve para reconciliar el contador con la realidad, por ejemplo tras una carga de datos.
     */
    @Transactional
    public void synchronize(UUID organizationId, UsageMetric metric, long value) {
        LocalDate today = LocalDate.now(clock);
        lockOrOpen(organizationId, metric, today).set(value);
    }

    /**
     * Bloquea el contador vigente de la metrica y lo crea si aun no existe.
     *
     * <p>Un tenant creado por una semilla SQL puede no tener contadores; abrirlos aqui evita que la
     * primera alta falle por una fila ausente.
     */
    private PlanUsage lockOrOpen(UUID organizationId, UsageMetric metric, LocalDate today) {
        return usages.lockCurrentUsage(organizationId, metric, today)
                .orElseGet(() -> usages.save(
                        PlanUsage.start(organizations.require(organizationId), metric, today, null)));
    }

    /** Limite del plan para una metrica; null si no hay contrato o el plan no impone limite. */
    private Long limitFor(Optional<SubscriptionPlan> plan, UsageMetric metric) {
        return plan.flatMap(p -> p.limitFor(metric)).orElse(null);
    }
}
