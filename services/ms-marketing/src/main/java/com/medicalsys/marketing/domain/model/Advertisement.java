package com.medicalsys.marketing.domain.model;

import com.medicalsys.marketing.common.error.BusinessRuleException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.domain.enums.AdvertisementPlacement;
import com.medicalsys.marketing.domain.enums.AdvertisementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Anuncio del portal del paciente ({@code advertisement}).
 *
 * <p>A diferencia de la campana, el anuncio es pasivo: no busca a nadie, se muestra a quien entra.
 * Por eso su publico objetivo es un filtro ligero -- edad, sexo, ciudad -- y no un segmento con
 * criterio completo. Un anuncio sin {@code organizationPublicId} es institucional de la plataforma y
 * se muestra en todos los tenants.
 *
 * <p>{@code imageDocumentPublicId} referencia a ms-documental sin FK: la imagen vive donde viven los
 * documentos, y marketing solo guarda su identificador.
 */
@Entity
@Table(name = "advertisement")
public class Advertisement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_document_public_id", columnDefinition = "BINARY(16)")
    private UUID imageDocumentPublicId;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Column(name = "service_offering_public_id", columnDefinition = "BINARY(16)")
    private UUID serviceOfferingPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "placement", nullable = false)
    private AdvertisementPlacement placement;

    @Column(name = "priority", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private short priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_audience", columnDefinition = "json")
    private Map<String, Object> targetAudience;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AdvertisementStatus status;

    @Column(name = "impressions", nullable = false, columnDefinition = "INT UNSIGNED")
    private long impressions;

    @Column(name = "clicks", nullable = false, columnDefinition = "INT UNSIGNED")
    private long clicks;

    @Column(name = "created_by_user_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID createdByUserPublicId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Advertisement() {
        // Requerido por JPA.
    }

    /** Crea un anuncio en borrador. */
    public static Advertisement draft(UUID organizationPublicId, String title, String content,
            UUID imageDocumentPublicId, String targetUrl, UUID serviceOfferingPublicId,
            AdvertisementPlacement placement, Integer priority, Map<String, Object> targetAudience,
            Instant validFrom, Instant validUntil, UUID createdByUserPublicId) {
        Advertisement advertisement = new Advertisement();
        advertisement.publicId = UUID.randomUUID();
        advertisement.organizationPublicId = organizationPublicId;
        advertisement.title = Objects.requireNonNull(title, "title");
        advertisement.content = content;
        advertisement.imageDocumentPublicId = imageDocumentPublicId;
        advertisement.targetUrl = targetUrl;
        advertisement.serviceOfferingPublicId = serviceOfferingPublicId;
        advertisement.placement = placement == null ? AdvertisementPlacement.PATIENT_PORTAL : placement;
        advertisement.priority = (short) (priority == null ? 5 : priority);
        advertisement.targetAudience = targetAudience;
        advertisement.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        advertisement.validUntil = Objects.requireNonNull(validUntil, "validUntil");
        advertisement.status = AdvertisementStatus.DRAFT;
        advertisement.impressions = 0L;
        advertisement.clicks = 0L;
        advertisement.createdByUserPublicId = createdByUserPublicId;
        return advertisement;
    }

    /** Publica el anuncio para que el portal empiece a servirlo. */
    public void publish() {
        if (status == AdvertisementStatus.FINISHED) {
            throw stateError("publicar", "un anuncio finalizado no vuelve a publicarse");
        }
        this.status = AdvertisementStatus.PUBLISHED;
    }

    /** Retira temporalmente el anuncio sin perder sus metricas. */
    public void pause() {
        if (status != AdvertisementStatus.PUBLISHED) {
            throw stateError("pausar", "solo un anuncio publicado puede pausarse");
        }
        this.status = AdvertisementStatus.PAUSED;
    }

    /** Cierra el anuncio de forma definitiva. */
    public void finish() {
        this.status = AdvertisementStatus.FINISHED;
    }

    /** Suma una impresion. Solo cuenta si el anuncio esta visible en ese instante. */
    public void registerImpression(Instant at) {
        requireVisible(at, "registrar la impresion");
        impressions++;
    }

    /** Suma un clic. Solo cuenta si el anuncio esta visible en ese instante. */
    public void registerClick(Instant at) {
        requireVisible(at, "registrar el clic");
        clicks++;
    }

    /** Indica si el anuncio debe servirse en el instante indicado. */
    public boolean isLiveAt(Instant at) {
        return status.isVisible() && !at.isBefore(validFrom) && at.isBefore(validUntil);
    }

    /** Indica si el anuncio pertenece al tenant, o es institucional y por tanto de todos. */
    public boolean isVisibleTo(UUID candidateOrganizationId) {
        return organizationPublicId == null || organizationPublicId.equals(candidateOrganizationId);
    }

    private void requireVisible(Instant at, String action) {
        if (!isLiveAt(at)) {
            throw stateError(action, "el anuncio no esta publicado o esta fuera de su vigencia");
        }
    }

    private BusinessRuleException stateError(String action, String reason) {
        return new BusinessRuleException(ErrorCode.ADVERTISEMENT_STATE_NOT_ALLOWED,
                "No se puede " + action + " del anuncio '" + title + "': " + reason);
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

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public UUID getImageDocumentPublicId() {
        return imageDocumentPublicId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public UUID getServiceOfferingPublicId() {
        return serviceOfferingPublicId;
    }

    public AdvertisementPlacement getPlacement() {
        return placement;
    }

    public short getPriority() {
        return priority;
    }

    public Map<String, Object> getTargetAudience() {
        return targetAudience;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public AdvertisementStatus getStatus() {
        return status;
    }

    public long getImpressions() {
        return impressions;
    }

    public long getClicks() {
        return clicks;
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
