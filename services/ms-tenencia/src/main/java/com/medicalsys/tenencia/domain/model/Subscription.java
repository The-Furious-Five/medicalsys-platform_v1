package com.medicalsys.tenencia.domain.model;

import com.medicalsys.tenencia.common.Money;
import com.medicalsys.tenencia.domain.enums.BillingCycle;
import com.medicalsys.tenencia.domain.enums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Contrato vigente entre una organizacion y un plan ({@code subscription}).
 *
 * <p>El precio pactado se copia en {@code agreed_price_bob} y no se lee del plan al facturar: si
 * manana sube la tarifa de lista, el contrato ya firmado conserva su importe.
 *
 * <p>{@code end_date} nulo significa vigente, y el CHECK del diccionario impide que la fecha de fin
 * sea anterior a la de inicio.
 */
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "agreed_price_bob", nullable = false, precision = 10, scale = 2)
    private BigDecimal agreedPriceBob;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Subscription() {
        // Requerido por JPA.
    }

    /**
     * Abre una suscripcion en periodo de prueba.
     *
     * @param organization   tenant contratante
     * @param plan           plan contratado
     * @param billingCycle   periodicidad del cobro
     * @param startDate      inicio de vigencia
     * @param endDate        fin de vigencia; null = vigente indefinidamente
     * @param agreedPriceBob precio pactado; si es null se toma el de lista del plan
     */
    public static Subscription open(Organization organization, SubscriptionPlan plan, BillingCycle billingCycle,
            LocalDate startDate, LocalDate endDate, BigDecimal agreedPriceBob) {
        Subscription subscription = new Subscription();
        subscription.organization = Objects.requireNonNull(organization, "organization");
        subscription.plan = Objects.requireNonNull(plan, "plan");
        subscription.billingCycle = billingCycle == null ? BillingCycle.MONTHLY : billingCycle;
        subscription.startDate = Objects.requireNonNull(startDate, "startDate");
        subscription.endDate = endDate;
        subscription.agreedPriceBob = Money.amount(
                agreedPriceBob == null ? plan.getMonthlyPriceBob() : agreedPriceBob);
        subscription.status = SubscriptionStatus.TRIAL;
        return subscription;
    }

    /** Cambia el estado del contrato; al cancelar se cierra tambien su vigencia. */
    public void changeStatus(SubscriptionStatus newStatus, LocalDate today) {
        this.status = Objects.requireNonNull(newStatus, "status");
        if (newStatus == SubscriptionStatus.CANCELLED && endDate == null) {
            this.endDate = today;
        }
    }

    /** Migra el contrato a otro plan, con su nuevo precio pactado. */
    public void changePlan(SubscriptionPlan newPlan, BigDecimal newAgreedPriceBob) {
        this.plan = Objects.requireNonNull(newPlan, "plan");
        this.agreedPriceBob = Money.amount(
                newAgreedPriceBob == null ? newPlan.getMonthlyPriceBob() : newAgreedPriceBob);
    }

    /** Indica si el contrato habilita el uso del producto en la fecha indicada. */
    public boolean isEffectiveOn(LocalDate date) {
        if (!status.grantsService()) {
            return false;
        }
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }

    public boolean isCurrent() {
        return status.isCurrent();
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getAgreedPriceBob() {
        return agreedPriceBob;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
