package com.medicalsys.marketing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Replica local de {@code tenancy_db.service_offering} ({@code ref_service_offering}).
 *
 * <p>Marketing necesita el nombre y el precio de la prestacion para dos cosas: segmentar por
 * "ultimo servicio comprado" y calcular el descuento de un cupon atado a una prestacion. Nada mas.
 * El precio es una copia; el propietario del catalogo sigue siendo ms-tenencia.
 */
@Entity
@Table(name = "ref_service_offering")
public class RefServiceOffering {

    @Id
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "base_price_bob", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePriceBob;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "synced_at", insertable = false, updatable = false)
    private Instant syncedAt;

    protected RefServiceOffering() {
        // Requerido por JPA.
    }

    /** Crea la replica de una prestacion recien conocida. */
    public static RefServiceOffering of(UUID publicId, UUID organizationPublicId, String name,
            BigDecimal basePriceBob, boolean active) {
        RefServiceOffering offering = new RefServiceOffering();
        offering.publicId = Objects.requireNonNull(publicId, "publicId");
        offering.organizationPublicId = Objects.requireNonNull(organizationPublicId, "organizationPublicId");
        offering.name = Objects.requireNonNull(name, "name");
        offering.basePriceBob = basePriceBob == null ? BigDecimal.ZERO : basePriceBob;
        offering.active = active;
        offering.sourceVersion = 0L;
        return offering;
    }

    /**
     * Aplica una version del propietario. Un evento mas viejo que la version aplicada se ignora.
     *
     * @return {@code true} si la replica cambio
     */
    public boolean apply(String name, BigDecimal basePriceBob, Boolean active, long sourceVersion) {
        if (sourceVersion < this.sourceVersion) {
            return false;
        }
        if (name != null) {
            this.name = name;
        }
        if (basePriceBob != null) {
            this.basePriceBob = basePriceBob;
        }
        if (active != null) {
            this.active = active;
        }
        this.sourceVersion = sourceVersion;
        return true;
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

    public BigDecimal getBasePriceBob() {
        return basePriceBob;
    }

    public boolean isActive() {
        return active;
    }

    public long getSourceVersion() {
        return sourceVersion;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
