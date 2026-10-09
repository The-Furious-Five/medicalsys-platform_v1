package com.medicalsys.marketing.domain.model;

import com.medicalsys.marketing.privacy.SegmentCriteria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Audiencia definida por un criterio declarativo ({@code audience_segment}).
 *
 * <p>El criterio se guarda como JSON tipado y no como SQL: quien crea un segmento describe
 * <em>que</em> quiere, y es este servicio -- que si conoce la frontera -- el que decide <em>como</em>
 * traducirlo a una consulta. Si el criterio fuera SQL o un filtro libre, la lista blanca de
 * {@code SegmentCriteriaField} no serviria de nada.
 *
 * <p>{@code usesClinicalData} es la segunda barrera declarativa heredada del monolito. La columna
 * lleva {@code CHECK (uses_clinical_data = 0)} y este campo no tiene setter: para violar la promesa
 * habria que modificar el codigo y la migracion, no solo olvidarse de un WHERE.
 */
@Entity
@Table(name = "audience_segment")
public class AudienceSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria", nullable = false, columnDefinition = "json")
    private SegmentCriteria criteria;

    /** Invariante de privacidad: siempre 0. No existe operacion que lo cambie. */
    @Column(name = "uses_clinical_data", nullable = false, updatable = false)
    private boolean usesClinicalData;

    @Column(name = "estimated_size", columnDefinition = "INT UNSIGNED")
    private Long estimatedSize;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by_user_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID createdByUserPublicId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected AudienceSegment() {
        // Requerido por JPA.
    }

    /** Define un segmento activo sobre un criterio ya validado por el guardian de privacidad. */
    public static AudienceSegment define(UUID organizationPublicId, String name, String description,
            SegmentCriteria criteria, UUID createdByUserPublicId) {
        AudienceSegment segment = new AudienceSegment();
        segment.publicId = UUID.randomUUID();
        segment.organizationPublicId = Objects.requireNonNull(organizationPublicId, "organizationPublicId");
        segment.name = Objects.requireNonNull(name, "name");
        segment.description = description;
        segment.criteria = Objects.requireNonNull(criteria, "criteria");
        segment.usesClinicalData = false;
        segment.active = true;
        segment.createdByUserPublicId = createdByUserPublicId;
        return segment;
    }

    /** Reemplaza el criterio; el nuevo ya paso por el guardian. */
    public void redefine(String description, SegmentCriteria criteria) {
        if (description != null) {
            this.description = description;
        }
        if (criteria != null) {
            this.criteria = criteria;
            this.estimatedSize = null;
            this.lastEvaluatedAt = null;
        }
    }

    /** Guarda el resultado de la ultima evaluacion del criterio. */
    public void recordEvaluation(long estimatedSize, Instant evaluatedAt) {
        this.estimatedSize = estimatedSize;
        this.lastEvaluatedAt = evaluatedAt;
    }

    public void activate() {
        this.active = true;
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

    public String getDescription() {
        return description;
    }

    public SegmentCriteria getCriteria() {
        return criteria;
    }

    public boolean usesClinicalData() {
        return usesClinicalData;
    }

    public Long getEstimatedSize() {
        return estimatedSize;
    }

    public Instant getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getCreatedByUserPublicId() {
        return createdByUserPublicId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
