package com.medicalsys.historial.domain.model;

import com.medicalsys.historial.domain.enums.BackgroundType;
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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Antecedente personal, familiar o clinico del expediente ({@code medical_background}).
 *
 * <p>{@code title} es un resumen no sensible que permite indexar; el detalle viaja cifrado.
 */
@Entity
@Table(name = "medical_background")
public class MedicalBackground {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medical_record_id", nullable = false, updatable = false)
    private MedicalRecord medicalRecord;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private BackgroundType type;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "encrypted_detail", length = 4096)
    private byte[] encryptedDetail;

    @Column(name = "icd10_code", length = 10)
    private String icd10Code;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "recorded_by_professional_public_id", columnDefinition = "BINARY(16)")
    private UUID recordedByProfessionalPublicId;

    @Generated(event = EventType.INSERT)
    @Column(name = "recorded_at", insertable = false, updatable = false)
    private Instant recordedAt;

    protected MedicalBackground() {
        // Requerido por JPA.
    }

    public static MedicalBackground record(MedicalRecord medicalRecord, BackgroundType type, String title,
            byte[] encryptedDetail, String icd10Code, LocalDate eventDate,
            UUID recordedByProfessionalPublicId) {
        MedicalBackground background = new MedicalBackground();
        background.medicalRecord = Objects.requireNonNull(medicalRecord, "medicalRecord");
        background.type = Objects.requireNonNull(type, "type");
        background.title = Objects.requireNonNull(title, "title");
        background.encryptedDetail = encryptedDetail;
        background.icd10Code = icd10Code;
        background.eventDate = eventDate;
        background.active = true;
        background.recordedByProfessionalPublicId = recordedByProfessionalPublicId;
        return background;
    }

    /** Marca el antecedente como no vigente conservando su historia. */
    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public MedicalRecord getMedicalRecord() {
        return medicalRecord;
    }

    public BackgroundType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public byte[] getEncryptedDetail() {
        return encryptedDetail;
    }

    public String getIcd10Code() {
        return icd10Code;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getRecordedByProfessionalPublicId() {
        return recordedByProfessionalPublicId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
