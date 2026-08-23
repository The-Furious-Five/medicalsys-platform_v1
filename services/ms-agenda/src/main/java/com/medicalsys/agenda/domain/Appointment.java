package com.medicalsys.agenda.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Reserva de atencion entre paciente, profesional y prestacion. */
@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "organization_public_id", nullable = false)
    private UUID organizationPublicId;

    @Column(name = "branch_public_id", nullable = false)
    private UUID branchPublicId;

    @Column(name = "patient_public_id", nullable = false)
    private UUID patientPublicId;

    @Column(name = "health_professional_public_id", nullable = false)
    private UUID healthProfessionalPublicId;

    @Column(name = "service_offering_public_id", nullable = false)
    private UUID serviceOfferingPublicId;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false, length = 20)
    private Modality modality = Modality.IN_PERSON;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_origin", nullable = false, length = 20)
    private ChannelOrigin channelOrigin = ChannelOrigin.RECEPTION;

    @Column(name = "reason_for_visit", length = 300)
    private String reasonForVisit;

    @Column(name = "requires_consent", nullable = false)
    private boolean requiresConsent;

    @Column(name = "estimated_price_bob", precision = 12, scale = 2)
    private BigDecimal estimatedPriceBob;

    @Column(name = "teleconsultation_link", length = 400)
    private String teleconsultationLink;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_appointment_id")
    private Appointment sourceAppointment;

    @Column(name = "created_by_user_public_id")
    private UUID createdByUserPublicId;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @Column(name = "care_started_at")
    private OffsetDateTime careStartedAt;

    @Column(name = "care_ended_at")
    private OffsetDateTime careEndedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancelled_by_user_public_id")
    private UUID cancelledByUserPublicId;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("occurredAt asc")
    private List<AppointmentStatusHistory> statusHistory = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public void setOrganizationPublicId(UUID organizationPublicId) {
        this.organizationPublicId = organizationPublicId;
    }

    public UUID getBranchPublicId() {
        return branchPublicId;
    }

    public void setBranchPublicId(UUID branchPublicId) {
        this.branchPublicId = branchPublicId;
    }

    public UUID getPatientPublicId() {
        return patientPublicId;
    }

    public void setPatientPublicId(UUID patientPublicId) {
        this.patientPublicId = patientPublicId;
    }

    public UUID getHealthProfessionalPublicId() {
        return healthProfessionalPublicId;
    }

    public void setHealthProfessionalPublicId(UUID healthProfessionalPublicId) {
        this.healthProfessionalPublicId = healthProfessionalPublicId;
    }

    public UUID getServiceOfferingPublicId() {
        return serviceOfferingPublicId;
    }

    public void setServiceOfferingPublicId(UUID serviceOfferingPublicId) {
        this.serviceOfferingPublicId = serviceOfferingPublicId;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(OffsetDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(OffsetDateTime endsAt) {
        this.endsAt = endsAt;
    }

    public Modality getModality() {
        return modality;
    }

    public void setModality(Modality modality) {
        this.modality = modality;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public ChannelOrigin getChannelOrigin() {
        return channelOrigin;
    }

    public void setChannelOrigin(ChannelOrigin channelOrigin) {
        this.channelOrigin = channelOrigin;
    }

    public String getReasonForVisit() {
        return reasonForVisit;
    }

    public void setReasonForVisit(String reasonForVisit) {
        this.reasonForVisit = reasonForVisit;
    }

    public boolean isRequiresConsent() {
        return requiresConsent;
    }

    public void setRequiresConsent(boolean requiresConsent) {
        this.requiresConsent = requiresConsent;
    }

    public BigDecimal getEstimatedPriceBob() {
        return estimatedPriceBob;
    }

    public void setEstimatedPriceBob(BigDecimal estimatedPriceBob) {
        this.estimatedPriceBob = estimatedPriceBob;
    }

    public String getTeleconsultationLink() {
        return teleconsultationLink;
    }

    public void setTeleconsultationLink(String teleconsultationLink) {
        this.teleconsultationLink = teleconsultationLink;
    }

    public Appointment getSourceAppointment() {
        return sourceAppointment;
    }

    public void setSourceAppointment(Appointment sourceAppointment) {
        this.sourceAppointment = sourceAppointment;
    }

    public UUID getCreatedByUserPublicId() {
        return createdByUserPublicId;
    }

    public void setCreatedByUserPublicId(UUID createdByUserPublicId) {
        this.createdByUserPublicId = createdByUserPublicId;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public OffsetDateTime getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(OffsetDateTime arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public OffsetDateTime getCareStartedAt() {
        return careStartedAt;
    }

    public void setCareStartedAt(OffsetDateTime careStartedAt) {
        this.careStartedAt = careStartedAt;
    }

    public OffsetDateTime getCareEndedAt() {
        return careEndedAt;
    }

    public void setCareEndedAt(OffsetDateTime careEndedAt) {
        this.careEndedAt = careEndedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(OffsetDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public UUID getCancelledByUserPublicId() {
        return cancelledByUserPublicId;
    }

    public void setCancelledByUserPublicId(UUID cancelledByUserPublicId) {
        this.cancelledByUserPublicId = cancelledByUserPublicId;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<AppointmentStatusHistory> getStatusHistory() {
        return statusHistory;
    }
}
