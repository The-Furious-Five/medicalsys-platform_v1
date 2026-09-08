package com.medicalsys.marketing.domain.model;

import com.medicalsys.marketing.common.error.BusinessRuleException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.domain.enums.CampaignChannel;
import com.medicalsys.marketing.domain.enums.CampaignStatus;
import com.medicalsys.marketing.domain.enums.CampaignType;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Campana comercial o de salud preventiva ({@code campaign}).
 *
 * <p>Una campana no envia mensajes: elige a quien, con que plantilla y por que canal, y publica el
 * evento {@code marketing.campana}. Quien tiene credenciales del proveedor -- y la responsabilidad de
 * la ventana de 24 h de WhatsApp -- es ms-notificaciones. Separarlo asi es lo que permite que una
 * caida de Meta no toque a este servicio, ni al reves.
 *
 * <p>Las transiciones de estado viven en la entidad y no en el servicio: el ciclo de vida es una
 * invariante del agregado, y tenerlo aqui evita que una ruta nueva se salte una comprobacion.
 *
 * <p>Los contadores son proyecciones alimentadas por eventos de vuelta: {@code totalSent} y
 * {@code totalRead} llegan de ms-notificaciones; {@code totalConverted}, de ms-agenda cuando una cita
 * se atribuye a la campana. Ninguno de esos eventos trae el motivo de la cita.
 */
@Entity
@Table(name = "campaign")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CampaignType type;

    @Column(name = "objective", length = 300)
    private String objective;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private CampaignChannel channel;

    @Column(name = "message_template_public_id", columnDefinition = "BINARY(16)")
    private UUID messageTemplatePublicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audience_segment_id")
    private AudienceSegment audienceSegment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "budget_bob", precision = 12, scale = 2)
    private BigDecimal budgetBob;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CampaignStatus status;

    @Column(name = "total_recipients", nullable = false, columnDefinition = "INT UNSIGNED")
    private long totalRecipients;

    @Column(name = "total_sent", nullable = false, columnDefinition = "INT UNSIGNED")
    private long totalSent;

    @Column(name = "total_read", nullable = false, columnDefinition = "INT UNSIGNED")
    private long totalRead;

    @Column(name = "total_converted", nullable = false, columnDefinition = "INT UNSIGNED")
    private long totalConverted;

    @Column(name = "created_by_user_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID createdByUserPublicId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Campaign() {
        // Requerido por JPA.
    }

    /** Crea una campana en borrador. Nada sale hasta que alguien la lance explicitamente. */
    public static Campaign draft(UUID organizationPublicId, String name, CampaignType type, String objective,
            CampaignChannel channel, UUID messageTemplatePublicId, AudienceSegment audienceSegment,
            Promotion promotion, Instant startsAt, Instant endsAt, BigDecimal budgetBob,
            UUID createdByUserPublicId) {
        Campaign campaign = new Campaign();
        campaign.publicId = UUID.randomUUID();
        campaign.organizationPublicId = Objects.requireNonNull(organizationPublicId, "organizationPublicId");
        campaign.name = Objects.requireNonNull(name, "name");
        campaign.type = Objects.requireNonNull(type, "type");
        campaign.objective = objective;
        campaign.channel = channel == null ? CampaignChannel.WHATSAPP : channel;
        campaign.messageTemplatePublicId = messageTemplatePublicId;
        campaign.audienceSegment = audienceSegment;
        campaign.promotion = promotion;
        campaign.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        campaign.endsAt = endsAt;
        campaign.budgetBob = budgetBob;
        campaign.status = CampaignStatus.DRAFT;
        campaign.totalRecipients = 0L;
        campaign.totalSent = 0L;
        campaign.totalRead = 0L;
        campaign.totalConverted = 0L;
        campaign.createdByUserPublicId = createdByUserPublicId;
        return campaign;
    }

    /** Fija el total de destinatarios tras materializar la audiencia. */
    public void recordAudience(long reachableRecipients) {
        requireAudienceRefreshAllowed();
        this.totalRecipients = reachableRecipients;
    }

    /** Programa la campana para que arranque en su fecha de inicio. */
    public void schedule() {
        if (status != CampaignStatus.DRAFT) {
            throw stateError("programar", "solo una campana en BORRADOR puede programarse");
        }
        this.status = CampaignStatus.SCHEDULED;
    }

    /**
     * Pone la campana en ejecucion.
     *
     * <p>Exige audiencia materializada: lanzar sin destinatarios generaria un evento que ningun
     * consumidor podria cumplir, y el fallo aparecería en ms-notificaciones en vez de aqui.
     */
    public void launch() {
        if (!status.allowsLaunch()) {
            throw stateError("lanzar", "una campana en estado " + status + " ya no puede lanzarse");
        }
        if (totalRecipients == 0) {
            throw new BusinessRuleException(ErrorCode.CAMPAIGN_WITHOUT_AUDIENCE,
                    "La campana no tiene destinatarios. Materialice la audiencia antes de lanzarla");
        }
        this.status = CampaignStatus.RUNNING;
    }

    /** Detiene temporalmente el envio; la audiencia y los contadores se conservan. */
    public void pause() {
        if (status != CampaignStatus.RUNNING) {
            throw stateError("pausar", "solo una campana EN_EJECUCION puede pausarse");
        }
        this.status = CampaignStatus.PAUSED;
    }

    /** Cierra la campana de forma definitiva. */
    public void finish() {
        if (status.isTerminal()) {
            throw stateError("finalizar", "la campana ya esta en un estado terminal");
        }
        this.status = CampaignStatus.FINISHED;
    }

    /** Cancela la campana. Una campana cancelada no vuelve a ejecutarse. */
    public void cancel() {
        if (status.isTerminal()) {
            throw stateError("cancelar", "la campana ya esta en un estado terminal");
        }
        this.status = CampaignStatus.CANCELLED;
    }

    /** Contabiliza un mensaje entregado, informado por ms-notificaciones. */
    public void registerSent() {
        totalSent++;
    }

    /** Contabiliza una lectura, informada por ms-notificaciones. */
    public void registerRead() {
        totalRead++;
    }

    /** Contabiliza una conversion: una cita atribuida a la campana. */
    public void registerConversion() {
        totalConverted++;
    }

    /** Comprueba que la campana admita recalcular su audiencia. */
    public void requireAudienceRefreshAllowed() {
        if (!status.allowsAudienceRefresh()) {
            throw stateError("recalcular la audiencia",
                    "una campana en estado " + status + " tiene su audiencia congelada");
        }
    }

    /** Indica si la campana pertenece al tenant indicado. */
    public boolean belongsTo(UUID candidateOrganizationId) {
        return organizationPublicId.equals(candidateOrganizationId);
    }

    private BusinessRuleException stateError(String action, String reason) {
        return new BusinessRuleException(ErrorCode.CAMPAIGN_STATE_NOT_ALLOWED,
                "No se puede " + action + " la campana '" + name + "': " + reason);
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

    public CampaignType getType() {
        return type;
    }

    public String getObjective() {
        return objective;
    }

    public CampaignChannel getChannel() {
        return channel;
    }

    public UUID getMessageTemplatePublicId() {
        return messageTemplatePublicId;
    }

    public AudienceSegment getAudienceSegment() {
        return audienceSegment;
    }

    public Promotion getPromotion() {
        return promotion;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public BigDecimal getBudgetBob() {
        return budgetBob;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public long getTotalRecipients() {
        return totalRecipients;
    }

    public long getTotalSent() {
        return totalSent;
    }

    public long getTotalRead() {
        return totalRead;
    }

    public long getTotalConverted() {
        return totalConverted;
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
