package com.medicalsys.historial.domain.model;

import com.medicalsys.historial.domain.enums.AddendumReason;
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
import org.hibernate.annotations.Immutable;
import org.hibernate.generator.EventType;

/**
 * Correccion o adicion inmutable posterior al cierre de un encuentro ({@code encounter_addendum}).
 *
 * <p>Es el unico mecanismo admitido para corregir un encuentro firmado: el registro original nunca
 * se sobrescribe.
 */
@Entity
@Table(name = "encounter_addendum")
@Immutable
public class EncounterAddendum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinical_encounter_id", nullable = false, updatable = false)
    private ClinicalEncounter clinicalEncounter;

    @Column(name = "health_professional_public_id", nullable = false, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID healthProfessionalPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false)
    private AddendumReason reason;

    @Column(name = "encrypted_text", nullable = false, updatable = false, length = 8192)
    private byte[] encryptedText;

    @Column(name = "encryption_key_public_id", nullable = false, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID encryptionKeyPublicId;

    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected EncounterAddendum() {
        // Requerido por JPA.
    }

    public static EncounterAddendum append(ClinicalEncounter encounter, UUID healthProfessionalPublicId,
            AddendumReason reason, byte[] encryptedText, UUID encryptionKeyPublicId, String contentHash) {
        EncounterAddendum addendum = new EncounterAddendum();
        addendum.clinicalEncounter = Objects.requireNonNull(encounter, "clinicalEncounter");
        addendum.healthProfessionalPublicId = Objects.requireNonNull(healthProfessionalPublicId,
                "healthProfessionalPublicId");
        addendum.reason = Objects.requireNonNull(reason, "reason");
        addendum.encryptedText = Objects.requireNonNull(encryptedText, "encryptedText");
        addendum.encryptionKeyPublicId = Objects.requireNonNull(encryptionKeyPublicId, "encryptionKeyPublicId");
        addendum.contentHash = Objects.requireNonNull(contentHash, "contentHash");
        return addendum;
    }

    public Long getId() {
        return id;
    }

    public ClinicalEncounter getClinicalEncounter() {
        return clinicalEncounter;
    }

    public UUID getHealthProfessionalPublicId() {
        return healthProfessionalPublicId;
    }

    public AddendumReason getReason() {
        return reason;
    }

    public byte[] getEncryptedText() {
        return encryptedText;
    }

    public UUID getEncryptionKeyPublicId() {
        return encryptionKeyPublicId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
