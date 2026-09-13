package com.medicalsys.facturacion.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Codigo Unico de Facturacion Diaria y su vigencia ({@code cufd}).
 *
 * <p>Cada factura referencia el CUFD vigente en su emision ({@code invoice.cufd_id NOT NULL}), y de
 * el sale la direccion que se imprime en el documento. En el entorno local lo produce el adaptador
 * fiscal simulado, con una vigencia acotada como la del codigo real.
 */
@Entity
@Table(name = "cufd")
public class Cufd {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "point_of_sale_id", nullable = false, updatable = false)
    private PointOfSale pointOfSale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuis_id", nullable = false, updatable = false)
    private Cuis cuis;

    @Column(name = "code", nullable = false, updatable = false, length = 200)
    private String code;

    @Column(name = "control_code", nullable = false, updatable = false, length = 30)
    private String controlCode;

    @Column(name = "address", nullable = false, length = 250)
    private String address;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private Instant validUntil;

    @Generated(event = EventType.INSERT)
    @Column(name = "requested_at", insertable = false, updatable = false)
    private Instant requestedAt;

    protected Cufd() {
        // Requerido por JPA.
    }

    /**
     * Registra un CUFD con su ventana de vigencia.
     *
     * @throws IllegalArgumentException si la ventana esta invertida; el CHECK de la base impone lo mismo
     */
    public static Cufd register(PointOfSale pointOfSale, Cuis cuis, String code, String controlCode,
            String address, Instant validFrom, Instant validUntil) {
        if (!Objects.requireNonNull(validUntil, "validUntil").isAfter(Objects.requireNonNull(validFrom, "validFrom"))) {
            throw new IllegalArgumentException("La vigencia del CUFD debe terminar despues de comenzar");
        }
        Cufd cufd = new Cufd();
        cufd.pointOfSale = Objects.requireNonNull(pointOfSale, "pointOfSale");
        cufd.cuis = Objects.requireNonNull(cuis, "cuis");
        cufd.code = Objects.requireNonNull(code, "code");
        cufd.controlCode = Objects.requireNonNull(controlCode, "controlCode");
        cufd.address = Objects.requireNonNull(address, "address");
        cufd.validFrom = validFrom;
        cufd.validUntil = validUntil;
        return cufd;
    }

    /** Indica si el codigo esta vigente en el instante indicado. */
    public boolean isValidAt(Instant instant) {
        return !instant.isBefore(validFrom) && instant.isBefore(validUntil);
    }

    public Long getId() {
        return id;
    }

    public PointOfSale getPointOfSale() {
        return pointOfSale;
    }

    public Cuis getCuis() {
        return cuis;
    }

    public String getCode() {
        return code;
    }

    public String getControlCode() {
        return controlCode;
    }

    public String getAddress() {
        return address;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
