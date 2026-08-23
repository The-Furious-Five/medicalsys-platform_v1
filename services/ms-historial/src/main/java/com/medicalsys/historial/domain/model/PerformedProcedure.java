package com.medicalsys.historial.domain.model;

import com.medicalsys.historial.domain.enums.AnesthesiaType;
import com.medicalsys.historial.domain.enums.ProcedureStatus;
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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Procedimiento ejecutado y documentado en el encuentro ({@code performed_procedure}). */
@Entity
@Table(name = "performed_procedure")
public class PerformedProcedure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinical_encounter_id", nullable = false, updatable = false)
    private ClinicalEncounter clinicalEncounter;

    @Column(name = "service_offering_public_id", nullable = false, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID serviceOfferingPublicId;

    @Column(name = "physical_resource_public_id", columnDefinition = "BINARY(16)")
    private UUID physicalResourcePublicId;

    @Column(name = "responsible_professional_public_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID responsibleProfessionalPublicId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "anesthesia_type", nullable = false)
    private AnesthesiaType anesthesiaType;

    @Column(name = "encrypted_description", length = 8192)
    private byte[] encryptedDescription;

    @Column(name = "encrypted_complications", length = 4096)
    private byte[] encryptedComplications;

    @Column(name = "informed_consent_public_id", columnDefinition = "BINARY(16)")
    private UUID informedConsentPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProcedureStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PerformedProcedure() {
        // Requerido por JPA.
    }

    public static PerformedProcedure record(ClinicalEncounter encounter, UUID serviceOfferingPublicId,
            UUID physicalResourcePublicId, UUID responsibleProfessionalPublicId, Instant startsAt, Instant endsAt,
            AnesthesiaType anesthesiaType, byte[] encryptedDescription, byte[] encryptedComplications,
            UUID informedConsentPublicId, ProcedureStatus status) {
        PerformedProcedure procedure = new PerformedProcedure();
        procedure.clinicalEncounter = Objects.requireNonNull(encounter, "clinicalEncounter");
        procedure.serviceOfferingPublicId = Objects.requireNonNull(serviceOfferingPublicId,
                "serviceOfferingPublicId");
        procedure.physicalResourcePublicId = physicalResourcePublicId;
        procedure.responsibleProfessionalPublicId = Objects.requireNonNull(responsibleProfessionalPublicId,
                "responsibleProfessionalPublicId");
        procedure.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        procedure.endsAt = endsAt;
        procedure.anesthesiaType = anesthesiaType == null ? AnesthesiaType.NONE : anesthesiaType;
        procedure.encryptedDescription = encryptedDescription;
        procedure.encryptedComplications = encryptedComplications;
        procedure.informedConsentPublicId = informedConsentPublicId;
        procedure.status = status == null ? ProcedureStatus.SCHEDULED : status;
        return procedure;
    }

    /** Cierra el procedimiento registrando su fin y su estado final. */
    public void close(Instant endsAt, ProcedureStatus status) {
        this.endsAt = endsAt;
        this.status = status == null ? ProcedureStatus.COMPLETED : status;
    }

    public Long getId() {
        return id;
    }

    public ClinicalEncounter getClinicalEncounter() {
        return clinicalEncounter;
    }

    public UUID getServiceOfferingPublicId() {
        return serviceOfferingPublicId;
    }

    public UUID getPhysicalResourcePublicId() {
        return physicalResourcePublicId;
    }

    public UUID getResponsibleProfessionalPublicId() {
        return responsibleProfessionalPublicId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public AnesthesiaType getAnesthesiaType() {
        return anesthesiaType;
    }

    public byte[] getEncryptedDescription() {
        return encryptedDescription;
    }

    public byte[] getEncryptedComplications() {
        return encryptedComplications;
    }

    public UUID getInformedConsentPublicId() {
        return informedConsentPublicId;
    }

    public ProcedureStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
