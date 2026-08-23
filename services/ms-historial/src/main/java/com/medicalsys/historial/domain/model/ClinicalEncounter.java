package com.medicalsys.historial.domain.model;

import com.medicalsys.historial.domain.enums.EncounterStatus;
import com.medicalsys.historial.domain.enums.EncounterType;
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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Registro clinico principal producido durante una atencion ({@code clinical_encounter}).
 *
 * <p>Raiz de agregado de addendums, signos vitales, diagnosticos, recetas, ordenes de estudio y
 * procedimientos. Toda la narrativa se persiste como ciphertext producido por ms-criptografia; la
 * entidad nunca ve ni expone el texto en claro.
 *
 * <p>Ciclo de vida: {@code DRAFT} admite edicion, {@code SIGNED} es inmutable y {@code VOIDED}
 * conserva el registro anulado. La inmutabilidad se refuerza en la base con un trigger, de modo que
 * un UPDATE directo tambien sea rechazado.
 */
@Entity
@Table(name = "clinical_encounter")
@DynamicUpdate
public class ClinicalEncounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medical_record_id", nullable = false, updatable = false)
    private MedicalRecord medicalRecord;

    @Column(name = "appointment_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID appointmentPublicId;

    @Column(name = "health_professional_public_id", nullable = false, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID healthProfessionalPublicId;

    @Column(name = "branch_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID branchPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private EncounterType type;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    /**
     * Motivo de consulta cifrado.
     *
     * <p>Corresponde a la columna {@code encrypted} del diccionario, cuyo tipo fisico documentado
     * ("A") no es valido en MySQL; se implementa como VARBINARY(8192), igual que las demas
     * narrativas del encuentro. La decision queda registrada en el README y en la migracion V1.
     */
    @Column(name = "encrypted", length = 8192)
    private byte[] encrypted;

    @Column(name = "encrypted_subjective_notes", length = 8192)
    private byte[] encryptedSubjectiveNotes;

    @Column(name = "encrypted_objective_notes", length = 8192)
    private byte[] encryptedObjectiveNotes;

    @Column(name = "encrypted_assessment", length = 8192)
    private byte[] encryptedAssessment;

    @Column(name = "encrypted_plan", length = 8192)
    private byte[] encryptedPlan;

    @Column(name = "encryption_key_public_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID encryptionKeyPublicId;

    @Column(name = "suggested_next_appointment_at")
    private LocalDate suggestedNextAppointmentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EncounterStatus status;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "digital_signature", length = 2048)
    private byte[] digitalSignature;

    @Column(name = "digital_certificate_public_id", columnDefinition = "BINARY(16)")
    private UUID digitalCertificatePublicId;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by_user_public_id", columnDefinition = "BINARY(16)")
    private UUID voidedByUserPublicId;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected ClinicalEncounter() {
        // Requerido por JPA.
    }

    /**
     * Abre un encuentro en borrador.
     *
     * @param medicalRecord               expediente al que pertenece
     * @param appointmentPublicId         cita de ms-agenda; null en urgencias o atencion espontanea
     * @param healthProfessionalPublicId  profesional que atiende
     * @param branchPublicId              sucursal donde ocurre la atencion
     * @param type                        tipo de encuentro
     * @param startsAt                    inicio de la atencion
     * @param encryptionKeyPublicId       clave con la que se cifra la narrativa
     */
    public static ClinicalEncounter open(MedicalRecord medicalRecord, UUID appointmentPublicId,
            UUID healthProfessionalPublicId, UUID branchPublicId, EncounterType type, Instant startsAt,
            UUID encryptionKeyPublicId) {
        ClinicalEncounter encounter = new ClinicalEncounter();
        encounter.publicId = UUID.randomUUID();
        encounter.medicalRecord = Objects.requireNonNull(medicalRecord, "medicalRecord");
        encounter.organizationPublicId = medicalRecord.getOrganizationPublicId();
        encounter.appointmentPublicId = appointmentPublicId;
        encounter.healthProfessionalPublicId = Objects.requireNonNull(healthProfessionalPublicId,
                "healthProfessionalPublicId");
        encounter.branchPublicId = Objects.requireNonNull(branchPublicId, "branchPublicId");
        encounter.type = Objects.requireNonNull(type, "type");
        encounter.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        encounter.encryptionKeyPublicId = Objects.requireNonNull(encryptionKeyPublicId, "encryptionKeyPublicId");
        encounter.status = EncounterStatus.DRAFT;
        return encounter;
    }

    /**
     * Reemplaza la narrativa cifrada del encuentro.
     *
     * <p>Solo se aceptan valores ya cifrados; la entidad no conoce el texto en claro. El llamador es
     * responsable de comprobar que el encuentro sea editable.
     */
    public void replaceNarrative(byte[] encrypted, byte[] subjective, byte[] objective, byte[] assessment,
            byte[] plan, UUID encryptionKeyPublicId) {
        this.encrypted = encrypted;
        this.encryptedSubjectiveNotes = subjective;
        this.encryptedObjectiveNotes = objective;
        this.encryptedAssessment = assessment;
        this.encryptedPlan = plan;
        this.encryptionKeyPublicId = Objects.requireNonNull(encryptionKeyPublicId, "encryptionKeyPublicId");
    }

    /** Actualiza los datos administrativos del encuentro mientras sigue en borrador. */
    public void updateSchedule(Instant endsAt, LocalDate suggestedNextAppointmentAt, EncounterType type) {
        if (type != null) {
            this.type = type;
        }
        this.endsAt = endsAt;
        this.suggestedNextAppointmentAt = suggestedNextAppointmentAt;
    }

    /**
     * Firma el encuentro y lo vuelve inmutable.
     *
     * @param contentHash                  SHA-256 del payload clinico firmado
     * @param digitalSignature             firma producida por ms-criptografia, opcional en el MVP
     * @param digitalCertificatePublicId   certificado usado, opcional en el MVP
     */
    public void sign(String contentHash, byte[] digitalSignature, UUID digitalCertificatePublicId) {
        this.status = EncounterStatus.SIGNED;
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
        this.signedAt = Instant.now();
        this.digitalSignature = digitalSignature;
        this.digitalCertificatePublicId = digitalCertificatePublicId;
        if (this.endsAt == null) {
            this.endsAt = this.signedAt;
        }
    }

    /** Anula el encuentro conservando la fila, el motivo y el responsable. */
    public void voidEncounter(UUID voidedByUserPublicId, String cancellationReason) {
        this.status = EncounterStatus.VOIDED;
        this.voidedAt = Instant.now();
        this.voidedByUserPublicId = voidedByUserPublicId;
        this.cancellationReason = cancellationReason;
    }

    public boolean isEditable() {
        return status.isEditable();
    }

    public boolean isSigned() {
        return status == EncounterStatus.SIGNED;
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

    public MedicalRecord getMedicalRecord() {
        return medicalRecord;
    }

    public UUID getAppointmentPublicId() {
        return appointmentPublicId;
    }

    public UUID getHealthProfessionalPublicId() {
        return healthProfessionalPublicId;
    }

    public UUID getBranchPublicId() {
        return branchPublicId;
    }

    public EncounterType getType() {
        return type;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public byte[] getEncrypted() {
        return encrypted;
    }

    public byte[] getEncryptedSubjectiveNotes() {
        return encryptedSubjectiveNotes;
    }

    public byte[] getEncryptedObjectiveNotes() {
        return encryptedObjectiveNotes;
    }

    public byte[] getEncryptedAssessment() {
        return encryptedAssessment;
    }

    public byte[] getEncryptedPlan() {
        return encryptedPlan;
    }

    public UUID getEncryptionKeyPublicId() {
        return encryptionKeyPublicId;
    }

    public LocalDate getSuggestedNextAppointmentAt() {
        return suggestedNextAppointmentAt;
    }

    public EncounterStatus getStatus() {
        return status;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public byte[] getDigitalSignature() {
        return digitalSignature;
    }

    public UUID getDigitalCertificatePublicId() {
        return digitalCertificatePublicId;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public UUID getVoidedByUserPublicId() {
        return voidedByUserPublicId;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
