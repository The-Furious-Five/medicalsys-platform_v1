package com.medicalsys.tenencia.domain.model;

import com.medicalsys.tenencia.domain.enums.UsageMetric;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Proyeccion local de una cuota consumida por una organizacion ({@code plan_usage}).
 *
 * <p>Tabla nueva del modelo distribuido. Quien consume la cuota vive en otra base: los usuarios en
 * identity_db y el almacenamiento en documents_db. Sin FK entre bases, la unica forma de saber si
 * queda espacio es materializar aqui el valor y contrastarlo con el limite del plan.
 *
 * <p>{@code version} implementa el bloqueo optimista que pide el diccionario: dos incrementos
 * simultaneos de la misma cuota no pueden pisarse. El segundo falla y se reintenta, en vez de
 * perder una unidad y dejar pasar un usuario por encima del limite.
 */
@Entity
@Table(name = "plan_usage")
public class PlanUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, updatable = false, length = 20)
    private UsageMetric metric;

    @Column(name = "current_value", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long currentValue;

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long version;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected PlanUsage() {
        // Requerido por JPA.
    }

    /**
     * Abre el contador de una cuota para un periodo.
     *
     * @param organization tenant medido
     * @param metric       cuota medida
     * @param periodStart  inicio del periodo de medicion
     * @param periodEnd    fin del periodo; null = periodo abierto
     */
    public static PlanUsage start(Organization organization, UsageMetric metric, LocalDate periodStart,
            LocalDate periodEnd) {
        PlanUsage usage = new PlanUsage();
        usage.organization = Objects.requireNonNull(organization, "organization");
        usage.metric = Objects.requireNonNull(metric, "metric");
        usage.periodStart = Objects.requireNonNull(periodStart, "periodStart");
        usage.periodEnd = periodEnd;
        usage.currentValue = 0L;
        return usage;
    }

    /**
     * Suma un delta al valor materializado.
     *
     * <p>El CHECK {@code ck_plan_usage_current_value} impide guardar un valor negativo, de modo que
     * un decremento excesivo se recorta a cero en vez de hacer fallar la transaccion.
     */
    public void add(long delta) {
        long updated = currentValue + delta;
        this.currentValue = Math.max(updated, 0L);
    }

    /** Fija el valor materializado, por ejemplo tras un recuento completo. */
    public void set(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("El consumo materializado no puede ser negativo");
        }
        this.currentValue = value;
    }

    /** Cierra el periodo de medicion. */
    public void closeOn(LocalDate date) {
        this.periodEnd = date;
    }

    /** Indica si sumar {@code delta} superaria el limite recibido. */
    public boolean wouldExceed(long delta, long limit) {
        return currentValue + delta > limit;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public UsageMetric getMetric() {
        return metric;
    }

    public Long getCurrentValue() {
        return currentValue;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
