package com.medicalsys.facturacion.domain.model;

import com.medicalsys.facturacion.domain.enums.CuisStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Codigo Unico de Inicio de Sistemas y su vigencia ({@code cuis}).
 *
 * <p>En el entorno local el valor lo produce el adaptador fiscal simulado. Ni esta entidad ni el
 * servicio implementan el algoritmo real del SIN ni consultan su servicio.
 */
@Entity
@Table(name = "cuis")
public class Cuis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "point_of_sale_id", nullable = false, updatable = false)
    private PointOfSale pointOfSale;

    @Column(name = "code", nullable = false, updatable = false, length = 30)
    private String code;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Generated(event = EventType.INSERT)
    @Column(name = "requested_at", insertable = false, updatable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CuisStatus status;

    protected Cuis() {
        // Requerido por JPA.
    }

    /** Registra un CUIS vigente para el punto de venta. */
    public static Cuis register(PointOfSale pointOfSale, String code, LocalDate validUntil) {
        Cuis cuis = new Cuis();
        cuis.pointOfSale = Objects.requireNonNull(pointOfSale, "pointOfSale");
        cuis.code = Objects.requireNonNull(code, "code");
        cuis.validUntil = Objects.requireNonNull(validUntil, "validUntil");
        cuis.status = CuisStatus.ACTIVE;
        return cuis;
    }

    /** Indica si el codigo sigue vigente en la fecha indicada. */
    public boolean isValidOn(LocalDate date) {
        return status == CuisStatus.ACTIVE && !validUntil.isBefore(date);
    }

    public Long getId() {
        return id;
    }

    public PointOfSale getPointOfSale() {
        return pointOfSale;
    }

    public String getCode() {
        return code;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public CuisStatus getStatus() {
        return status;
    }
}
