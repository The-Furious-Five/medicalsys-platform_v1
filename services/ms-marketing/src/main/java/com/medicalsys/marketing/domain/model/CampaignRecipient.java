package com.medicalsys.marketing.domain.model;

import com.medicalsys.marketing.domain.enums.RecipientStatus;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Audiencia materializada de una campana, con el resultado individual ({@code campaign_recipient}).
 *
 * <p>La audiencia se congela al materializarla en vez de reevaluarse en cada envio. El motivo es de
 * auditoria: seis meses despues hay que poder responder a quien se le mando esta campana y por que
 * quedo fuera quien quedo fuera. Un criterio reevaluado daria una respuesta distinta cada vez.
 *
 * <p>Un paciente con opt-out entra igualmente en la tabla, pero con {@link RecipientStatus#EXCLUDED_OPT_OUT}
 * y su motivo. Dejar constancia de la exclusion es mas util que omitir la fila: prueba que la
 * exclusion se evaluo.
 */
@Entity
@Table(name = "campaign_recipient")
public class CampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false, updatable = false)
    private Campaign campaign;

    @Column(name = "patient_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID patientPublicId;

    @Column(name = "notification_public_id", columnDefinition = "BINARY(16)")
    private UUID notificationPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecipientStatus status;

    @Column(name = "exclusion_reason", length = 120)
    private String exclusionReason;

    @Column(name = "generated_appointment_public_id", columnDefinition = "BINARY(16)")
    private UUID generatedAppointmentPublicId;

    @Column(name = "evaluated_at", insertable = false, updatable = false)
    private Instant evaluatedAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected CampaignRecipient() {
        // Requerido por JPA.
    }

    /** Selecciona a un paciente que cumple el criterio y acepta comunicaciones comerciales. */
    public static CampaignRecipient selected(Campaign campaign, UUID patientPublicId) {
        CampaignRecipient recipient = base(campaign, patientPublicId);
        recipient.status = RecipientStatus.SELECTED;
        return recipient;
    }

    /** Deja constancia de un paciente excluido por haber pedido no recibir publicidad. */
    public static CampaignRecipient excludedByOptOut(Campaign campaign, UUID patientPublicId, String reason) {
        CampaignRecipient recipient = base(campaign, patientPublicId);
        recipient.status = RecipientStatus.EXCLUDED_OPT_OUT;
        recipient.exclusionReason = reason;
        return recipient;
    }

    private static CampaignRecipient base(Campaign campaign, UUID patientPublicId) {
        CampaignRecipient recipient = new CampaignRecipient();
        recipient.campaign = Objects.requireNonNull(campaign, "campaign");
        recipient.patientPublicId = Objects.requireNonNull(patientPublicId, "patientPublicId");
        return recipient;
    }

    /** Marca el mensaje como entregado y guarda el identificador que devolvio ms-notificaciones. */
    public void markSent(UUID notificationPublicId) {
        this.notificationPublicId = notificationPublicId;
        this.status = RecipientStatus.SENT;
    }

    /** Marca el mensaje como leido. Un destinatario ya convertido no retrocede a leido. */
    public void markRead() {
        if (status != RecipientStatus.CONVERTED) {
            this.status = RecipientStatus.READ;
        }
    }

    /** Marca el fallo de entrega informado por el proveedor. */
    public void markFailed(String reason) {
        this.status = RecipientStatus.FAILED;
        this.exclusionReason = reason;
    }

    /**
     * Atribuye una cita a la campana.
     *
     * <p>El parametro es el UUID de la cita y nada mas: la atribucion no necesita saber de que era
     * la cita, y esta firma garantiza que no puede enterarse.
     *
     * @return {@code true} si esta llamada produjo la conversion; {@code false} si ya estaba
     *         convertido, para que el contador de la campana no se incremente dos veces
     */
    public boolean markConverted(UUID appointmentPublicId) {
        if (status == RecipientStatus.CONVERTED) {
            return false;
        }
        this.generatedAppointmentPublicId = appointmentPublicId;
        this.status = RecipientStatus.CONVERTED;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public UUID getPatientPublicId() {
        return patientPublicId;
    }

    public UUID getNotificationPublicId() {
        return notificationPublicId;
    }

    public RecipientStatus getStatus() {
        return status;
    }

    public String getExclusionReason() {
        return exclusionReason;
    }

    public UUID getGeneratedAppointmentPublicId() {
        return generatedAppointmentPublicId;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
