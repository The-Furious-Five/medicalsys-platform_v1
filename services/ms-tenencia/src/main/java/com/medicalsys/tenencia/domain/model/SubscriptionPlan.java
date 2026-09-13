package com.medicalsys.tenencia.domain.model;

import com.medicalsys.tenencia.common.Money;
import com.medicalsys.tenencia.domain.enums.UsageMetric;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Plan comercial del SaaS ({@code subscription_plan}).
 *
 * <p>Es un catalogo global de la plataforma, no un dato por tenant: no lleva {@code organization_id}
 * ni {@code public_id}, tal como lo define el diccionario.
 *
 * <p>Un limite nulo significa ilimitado. Esa es la razon por la que {@link #limitFor(UsageMetric)}
 * devuelve un {@link Optional} en vez de un cero: cero seria "ninguno permitido", que es lo
 * contrario de lo que dice el diccionario.
 */
@Entity
@Table(name = "subscription_plan")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "SMALLINT UNSIGNED")
    private Short id;

    @Column(name = "code", nullable = false, updatable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "monthly_price_bob", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPriceBob;

    @Column(name = "max_users", columnDefinition = "SMALLINT UNSIGNED")
    private Integer maxUsers;

    @Column(name = "max_branches", columnDefinition = "SMALLINT UNSIGNED")
    private Integer maxBranches;

    @Column(name = "max_storage_gb", columnDefinition = "SMALLINT UNSIGNED")
    private Integer maxStorageGb;

    @Column(name = "includes_whatsapp", nullable = false)
    private boolean includesWhatsapp;

    @Column(name = "includes_billing", nullable = false)
    private boolean includesBilling;

    /** Caracteristicas libres del plan en JSON; el MVP las conserva sin interpretarlas. */
    @Column(name = "features", columnDefinition = "JSON")
    private String features;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected SubscriptionPlan() {
        // Requerido por JPA.
    }

    /**
     * Declara un plan comercial.
     *
     * @param code            codigo unico del plan, por ejemplo DEMO
     * @param name            nombre visible
     * @param monthlyPriceBob precio mensual en bolivianos; DECIMAL, nunca coma flotante
     * @param maxUsers        limite de usuarios; null = ilimitado
     * @param maxBranches     limite de sucursales; null = ilimitado
     * @param maxStorageGb    limite de almacenamiento en GB; null = ilimitado
     */
    public static SubscriptionPlan of(String code, String name, BigDecimal monthlyPriceBob, Integer maxUsers,
            Integer maxBranches, Integer maxStorageGb, boolean includesWhatsapp, boolean includesBilling) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.code = code;
        plan.name = name;
        plan.monthlyPriceBob = Money.amount(monthlyPriceBob);
        plan.maxUsers = maxUsers;
        plan.maxBranches = maxBranches;
        plan.maxStorageGb = maxStorageGb;
        plan.includesWhatsapp = includesWhatsapp;
        plan.includesBilling = includesBilling;
        plan.active = true;
        return plan;
    }

    /**
     * Limite del plan para una metrica, ya expresado en la unidad en que esa metrica se mide.
     *
     * @return vacio si el plan no impone limite para esa metrica
     */
    public Optional<Long> limitFor(UsageMetric metric) {
        Integer configured = switch (metric) {
            case USERS -> maxUsers;
            case BRANCHES -> maxBranches;
            case STORAGE_BYTES -> maxStorageGb;
        };
        return Optional.ofNullable(configured).map(metric::limitFromPlan);
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getMonthlyPriceBob() {
        return monthlyPriceBob;
    }

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public Integer getMaxBranches() {
        return maxBranches;
    }

    public Integer getMaxStorageGb() {
        return maxStorageGb;
    }

    public boolean isIncludesWhatsapp() {
        return includesWhatsapp;
    }

    public boolean isIncludesBilling() {
        return includesBilling;
    }

    public String getFeatures() {
        return features;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
