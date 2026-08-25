package com.medicalsys.tenencia.mapper;

import com.medicalsys.tenencia.common.DateTimes;
import com.medicalsys.tenencia.domain.model.Branch;
import com.medicalsys.tenencia.domain.model.Organization;
import com.medicalsys.tenencia.domain.model.PlanUsage;
import com.medicalsys.tenencia.domain.model.ServiceOffering;
import com.medicalsys.tenencia.domain.model.Subscription;
import com.medicalsys.tenencia.domain.model.SubscriptionPlan;
import com.medicalsys.tenencia.web.dto.BillableServiceResponse;
import com.medicalsys.tenencia.web.dto.BranchRef;
import com.medicalsys.tenencia.web.dto.BranchResponse;
import com.medicalsys.tenencia.web.dto.OrganizationRef;
import com.medicalsys.tenencia.web.dto.OrganizationResponse;
import com.medicalsys.tenencia.web.dto.PlanUsageResponse;
import com.medicalsys.tenencia.web.dto.SchedulableServiceResponse;
import com.medicalsys.tenencia.web.dto.ServiceOfferingResponse;
import com.medicalsys.tenencia.web.dto.SubscriptionPlanResponse;
import com.medicalsys.tenencia.web.dto.SubscriptionResponse;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Traduce entidades a los DTO del contrato.
 *
 * <p>Ninguna entidad JPA sale del servicio: el contrato declara solo los campos acordados y no
 * cambia porque cambie el mapeo interno. Tampoco sale ninguna clave numerica de organizacion,
 * sucursal o prestacion: hacia afuera solo viaja el {@code public_id}.
 */
@Component
public class TenancyMapper {

    public OrganizationResponse toResponse(Organization organization) {
        return new OrganizationResponse(
                organization.getPublicId(),
                organization.getCode(),
                organization.getType(),
                organization.getLegalName(),
                organization.getTradeName(),
                organization.getNit(),
                organization.getTradeRegistryNumber(),
                organization.getContactEmail(),
                organization.getPhoneE164(),
                organization.getCity(),
                organization.getDepartment(),
                organization.getTimeZone(),
                organization.getCurrency(),
                organization.getLogoUrl(),
                organization.getStatus(),
                DateTimes.toOffset(organization.getCreatedAt()),
                DateTimes.toOffset(organization.getUpdatedAt()));
    }

    public OrganizationRef toRef(Organization organization) {
        return new OrganizationRef(
                organization.getPublicId(),
                organization.getCode(),
                organization.getTradeName(),
                organization.getStatus(),
                organization.getTimeZone(),
                organization.getCurrency(),
                organization.isOperational());
    }

    public BranchResponse toResponse(Branch branch) {
        return new BranchResponse(
                branch.getPublicId(),
                branch.getOrganization().getPublicId(),
                branch.getCode(),
                branch.getName(),
                branch.isHeadquarters(),
                branch.getAddress(),
                branch.getCity(),
                branch.getDepartment(),
                branch.getPhoneE164(),
                branch.getLatitude(),
                branch.getLongitude(),
                branch.getOpeningHours(),
                branch.getStatus(),
                DateTimes.toOffset(branch.getCreatedAt()),
                DateTimes.toOffset(branch.getUpdatedAt()));
    }

    public BranchRef toRef(Branch branch) {
        return new BranchRef(
                branch.getPublicId(),
                branch.getOrganization().getPublicId(),
                branch.getCode(),
                branch.getName(),
                branch.isHeadquarters(),
                branch.getCity(),
                branch.isActive());
    }

    public ServiceOfferingResponse toResponse(ServiceOffering offering) {
        return new ServiceOfferingResponse(
                offering.getPublicId(),
                offering.getOrganization().getPublicId(),
                offering.getCode(),
                offering.getName(),
                offering.getDescription(),
                offering.getType(),
                offering.getSpecialtyCode(),
                offering.getDurationMinutes(),
                offering.getBasePriceBob(),
                offering.isRequiresConsent(),
                offering.getConsentTemplatePublicId(),
                offering.getRequiredResourceType(),
                offering.getTaxProductCode(),
                offering.getTaxUnitCode(),
                offering.isActive(),
                DateTimes.toOffset(offering.getCreatedAt()),
                DateTimes.toOffset(offering.getUpdatedAt()));
    }

    /** Proyeccion facturable consumida por ms-facturacion. */
    public BillableServiceResponse toBillable(ServiceOffering offering) {
        return new BillableServiceResponse(
                offering.getPublicId(),
                offering.getCode(),
                offering.getName(),
                offering.getBasePriceBob(),
                offering.getTaxProductCode(),
                taxUnitCode(offering.getTaxUnitCode()),
                offering.isActive());
    }

    /** Proyeccion agendable consumida por ms-agenda. */
    public SchedulableServiceResponse toSchedulable(ServiceOffering offering) {
        return new SchedulableServiceResponse(
                offering.getPublicId(),
                offering.getCode(),
                offering.getName(),
                offering.getType(),
                offering.getSpecialtyCode(),
                offering.getDurationMinutes(),
                offering.getRequiredResourceType(),
                offering.isRequiresConsent(),
                offering.getConsentTemplatePublicId(),
                offering.isActive());
    }

    public SubscriptionPlanResponse toResponse(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getMonthlyPriceBob(),
                plan.getMaxUsers(),
                plan.getMaxBranches(),
                plan.getMaxStorageGb(),
                plan.isIncludesWhatsapp(),
                plan.isIncludesBilling(),
                plan.isActive(),
                DateTimes.toOffset(plan.getCreatedAt()));
    }

    public SubscriptionResponse toResponse(Subscription subscription, LocalDate today) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getOrganization().getPublicId(),
                toResponse(subscription.getPlan()),
                subscription.getBillingCycle(),
                subscription.getStartDate(),
                subscription.getEndDate(),
                subscription.getAgreedPriceBob(),
                subscription.getStatus(),
                subscription.isEffectiveOn(today),
                DateTimes.toOffset(subscription.getCreatedAt()),
                DateTimes.toOffset(subscription.getUpdatedAt()));
    }

    /**
     * Consumo frente al limite del plan.
     *
     * @param limit limite vigente para la metrica; null si el plan no impone ninguno
     */
    public PlanUsageResponse toResponse(PlanUsage usage, Long limit) {
        long current = usage.getCurrentValue();
        Long remaining = limit == null ? null : Math.max(limit - current, 0L);
        return new PlanUsageResponse(
                usage.getOrganization().getPublicId(),
                usage.getMetric(),
                current,
                limit,
                remaining,
                limit != null && current > limit,
                usage.getPeriodStart(),
                usage.getPeriodEnd(),
                DateTimes.toOffset(usage.getUpdatedAt()));
    }

    /**
     * Traduce la unidad de medida del SIN al entero que espera ms-facturacion.
     *
     * <p>El diccionario guarda {@code tax_unit_code} como VARCHAR(10) porque el catalogo del SIN
     * podria incorporar codigos no numericos. El contrato interno lo publica como entero, asi que
     * un valor no numerico viaja como ausente en vez de romper la deserializacion del consumidor.
     */
    private Integer taxUnitCode(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(stored.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
