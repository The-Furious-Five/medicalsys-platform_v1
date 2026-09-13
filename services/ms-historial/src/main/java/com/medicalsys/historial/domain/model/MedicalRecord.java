package com.medicalsys.historial.domain.model;

import com.medicalsys.historial.domain.enums.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Expediente de un paciente dentro de una organizacion ({@code medical_record}).
 *
 * <p>Raiz de agregado de alergias y antecedentes. Es unico por pareja organizacion-paciente: una
 * segunda solicitud de apertura devuelve siempre el mismo expediente.
 */
@Entity
@Table(name = "medical_record")
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "patient_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID patientPublicId;

    @Column(name = "record_number", nullable = false, updatable = false, length = 30)
    private String recordNumber;

    @Column(name = "opened_on", nullable = false, updatable = false)
    private LocalDate openedOn;

    @Column(name = "opening_branch_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID openingBranchPublicId;

    @Column(name = "encryption_key_public_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID encryptionKeyPublicId;

    @Column(name = "shares_with_network", nullable = false)
    private boolean sharesWithNetwork;

    @Column(name = "sharing_authorized_at")
    private Instant sharingAuthorizedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecordStatus status;

    @Column(name = "retention_until")
    private LocalDate retentionUntil;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected MedicalRecord() {
        // Requerido por JPA.
    }

    /**
     * Abre un expediente nuevo.
     *
     * @param organizationPublicId  tenant propietario
     * @param patientPublicId       paciente, validado previamente contra ms-pacientes
     * @param recordNumber          correlativo legible dentro del tenant
     * @param openingBranchPublicId sucursal de apertura, opcional
     * @param encryptionKeyPublicId clave con la que se cifran las narrativas del expediente
     */
    public static MedicalRecord open(UUID organizationPublicId, UUID patientPublicId, String recordNumber,
            UUID openingBranchPublicId, UUID encryptionKeyPublicId) {
        MedicalRecord record = new MedicalRecord();
        record.publicId = UUID.randomUUID();
        record.organizationPublicId = Objects.requireNonNull(organizationPublicId, "organizationPublicId");
        record.patientPublicId = Objects.requireNonNull(patientPublicId, "patientPublicId");
        record.recordNumber = Objects.requireNonNull(recordNumber, "recordNumber");
        record.openingBranchPublicId = openingBranchPublicId;
        record.encryptionKeyPublicId = Objects.requireNonNull(encryptionKeyPublicId, "encryptionKeyPublicId");
        record.openedOn = LocalDate.now();
        record.status = RecordStatus.ACTIVE;
        record.sharesWithNetwork = false;
        return record;
    }

    /** Registra o revoca la autorizacion del paciente para compartir su historial con otros tenants. */
    public void updateNetworkSharing(boolean shares) {
        this.sharesWithNetwork = shares;
        this.sharingAuthorizedAt = shares ? Instant.now() : null;
    }

    /** Cambia el estado administrativo del expediente conservando el registro. */
    public void changeStatus(RecordStatus newStatus, LocalDate retentionUntil) {
        this.status = Objects.requireNonNull(newStatus, "status");
        this.retentionUntil = retentionUntil;
    }

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
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

    public UUID getPatientPublicId() {
        return patientPublicId;
    }

    public String getRecordNumber() {
        return recordNumber;
    }

    public LocalDate getOpenedOn() {
        return openedOn;
    }

    public UUID getOpeningBranchPublicId() {
        return openingBranchPublicId;
    }

    public UUID getEncryptionKeyPublicId() {
        return encryptionKeyPublicId;
    }

    public boolean isSharesWithNetwork() {
        return sharesWithNetwork;
    }

    public Instant getSharingAuthorizedAt() {
        return sharingAuthorizedAt;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public LocalDate getRetentionUntil() {
        return retentionUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
