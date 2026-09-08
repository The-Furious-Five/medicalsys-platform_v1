package com.medicalsys.marketing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Replica local de {@code tenancy_db.organization} ({@code ref_organization}).
 *
 * <p>Es una copia de solo lectura desde el punto de vista del negocio: ningun caso de uso de
 * marketing la modifica: se alimenta de eventos de ms-tenencia y de una hidratacion puntual cuando
 * llega un tenant todavia desconocido. Guardarla evita que listar campanas exija una llamada HTTP
 * por fila, y es lo que permite que marketing siga sirviendo si ms-tenencia esta caido -- que es
 * justo lo que se espera del servicio de menor criticidad del sistema.
 *
 * <p>{@code includesMarketing} proyecta {@code plan_subscription.includes_marketing}. Es el criterio
 * C5 -- la opcionalidad por contrato -- hecho dato consultable: con 0, este servicio deja de atender
 * al tenant aunque el contenedor este levantado.
 */
@Entity
@Table(name = "ref_organization")
public class RefOrganization {

    @Id
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "commercial_name", nullable = false, length = 180)
    private String commercialName;

    @Column(name = "includes_marketing", nullable = false)
    private boolean includesMarketing;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "synced_at", insertable = false, updatable = false)
    private Instant syncedAt;

    protected RefOrganization() {
        // Requerido por JPA.
    }

    /** Crea la replica de un tenant recien conocido. */
    public static RefOrganization of(UUID publicId, String commercialName, boolean includesMarketing) {
        RefOrganization organization = new RefOrganization();
        organization.publicId = Objects.requireNonNull(publicId, "publicId");
        organization.commercialName = Objects.requireNonNull(commercialName, "commercialName");
        organization.includesMarketing = includesMarketing;
        organization.sourceVersion = 0L;
        return organization;
    }

    /**
     * Aplica una version del propietario.
     *
     * <p>Una version igual o anterior a la ya aplicada se ignora: los eventos pueden llegar
     * desordenados y una replica no debe retroceder.
     *
     * @return {@code true} si la replica cambio
     */
    public boolean apply(String commercialName, Boolean includesMarketing, long sourceVersion) {
        if (sourceVersion < this.sourceVersion) {
            return false;
        }
        if (commercialName != null) {
            this.commercialName = commercialName;
        }
        if (includesMarketing != null) {
            this.includesMarketing = includesMarketing;
        }
        this.sourceVersion = sourceVersion;
        return true;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getCommercialName() {
        return commercialName;
    }

    public boolean includesMarketing() {
        return includesMarketing;
    }

    public long getSourceVersion() {
        return sourceVersion;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
