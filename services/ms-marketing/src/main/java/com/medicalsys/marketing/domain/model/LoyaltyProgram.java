package com.medicalsys.marketing.domain.model;

import com.medicalsys.marketing.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reglas del programa de fidelizacion de un tenant ({@code loyalty_program}).
 *
 * <p>Dos tasas gobiernan el programa: {@code pointsPerBob}, cuantos puntos otorga cada boliviano
 * facturado, y {@code bobPerPoint}, cuanto vale un punto al canjearlo. Estan separadas a proposito:
 * el margen del programa es la diferencia entre ambas, y tenerla explicita permite cambiarla sin
 * tocar codigo.
 */
@Entity
@Table(name = "loyalty_program")
public class LoyaltyProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "points_per_bob", nullable = false, precision = 8, scale = 4)
    private BigDecimal pointsPerBob;

    @Column(name = "bob_per_point", nullable = false, precision = 8, scale = 4)
    private BigDecimal bobPerPoint;

    @Column(name = "points_validity_months", columnDefinition = "SMALLINT UNSIGNED")
    private Integer pointsValidityMonths;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected LoyaltyProgram() {
        // Requerido por JPA.
    }

    /** Crea un programa activo con sus tasas de acumulacion y canje. */
    public static LoyaltyProgram create(UUID organizationPublicId, String name, BigDecimal pointsPerBob,
            BigDecimal bobPerPoint, Integer pointsValidityMonths) {
        LoyaltyProgram program = new LoyaltyProgram();
        program.publicId = UUID.randomUUID();
        program.organizationPublicId = Objects.requireNonNull(organizationPublicId, "organizationPublicId");
        program.name = Objects.requireNonNull(name, "name");
        program.pointsPerBob = Money.rate(pointsPerBob == null ? BigDecimal.ONE : pointsPerBob);
        program.bobPerPoint = Money.rate(bobPerPoint == null ? new BigDecimal("0.0100") : bobPerPoint);
        program.pointsValidityMonths = pointsValidityMonths;
        program.active = true;
        return program;
    }

    /**
     * Puntos que otorga un importe facturado.
     *
     * <p>Se trunca hacia abajo: los puntos son enteros y regalar la fraccion en cada factura acaba
     * costando mas de lo que el programa presupuesta.
     */
    public int pointsFor(BigDecimal billedAmountBob) {
        return Money.amount(billedAmountBob)
                .multiply(pointsPerBob)
                .setScale(0, RoundingMode.DOWN)
                .intValue();
    }

    /** Valor en bolivianos de una cantidad de puntos. */
    public BigDecimal valueOf(int points) {
        return Money.amount(bobPerPoint.multiply(BigDecimal.valueOf(points)));
    }

    /** Actualiza las tasas del programa. */
    public void updateRates(BigDecimal pointsPerBob, BigDecimal bobPerPoint, Integer pointsValidityMonths) {
        if (pointsPerBob != null) {
            this.pointsPerBob = Money.rate(pointsPerBob);
        }
        if (bobPerPoint != null) {
            this.bobPerPoint = Money.rate(bobPerPoint);
        }
        if (pointsValidityMonths != null) {
            this.pointsValidityMonths = pointsValidityMonths;
        }
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPointsPerBob() {
        return pointsPerBob;
    }

    public BigDecimal getBobPerPoint() {
        return bobPerPoint;
    }

    public Integer getPointsValidityMonths() {
        return pointsValidityMonths;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
