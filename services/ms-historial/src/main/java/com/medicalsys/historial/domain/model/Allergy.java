package com.medicalsys.historial.domain.model;

import com.medicalsys.historial.domain.enums.AllergySeverity;
import com.medicalsys.historial.domain.enums.AllergyStatus;
import com.medicalsys.historial.domain.enums.AllergyType;
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

/** Alergia clinicamente relevante del expediente ({@code allergy}). */
@Entity
@Table(name = "allergy")
public class Allergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medical_record_id", nullable = false, updatable = false)
    private MedicalRecord medicalRecord;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private AllergyType type;

    @Column(name = "substance", nullable = false, updatable = false, length = 160)
    private String substance;

    @Column(name = "medication_code", length = 30)
    private String medicationCode;

    @Column(name = "reaction", length = 300)
    private String reaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AllergySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AllergyStatus status;

    @Column(name = "recorded_by_professional_public_id", columnDefinition = "BINARY(16)")
    private UUID recordedByProfessionalPublicId;

    @Generated(event = EventType.INSERT)
    @Column(name = "recorded_at", insertable = false, updatable = false)
    private Instant recordedAt;

    protected Allergy() {
        // Requerido por JPA.
    }

    public static Allergy record(MedicalRecord medicalRecord, AllergyType type, String substance,
            String medicationCode, String reaction, AllergySeverity severity,
            UUID recordedByProfessionalPublicId) {
        Allergy allergy = new Allergy();
        allergy.medicalRecord = Objects.requireNonNull(medicalRecord, "medicalRecord");
        allergy.type = Objects.requireNonNull(type, "type");
        allergy.substance = Objects.requireNonNull(substance, "substance");
        allergy.medicationCode = medicationCode;
        allergy.reaction = reaction;
        allergy.severity = severity == null ? AllergySeverity.MILD : severity;
        allergy.status = AllergyStatus.ACTIVE;
        allergy.recordedByProfessionalPublicId = recordedByProfessionalPublicId;
        return allergy;
    }

    /** Actualiza severidad, reaccion y vigencia sin borrar el registro clinico. */
    public void reassess(AllergySeverity severity, String reaction, AllergyStatus status) {
        if (severity != null) {
            this.severity = severity;
        }
        if (reaction != null) {
            this.reaction = reaction;
        }
        if (status != null) {
            this.status = status;
        }
    }

    public Long getId() {
        return id;
    }

    public MedicalRecord getMedicalRecord() {
        return medicalRecord;
    }

    public AllergyType getType() {
        return type;
    }

    public String getSubstance() {
        return substance;
    }

    public String getMedicationCode() {
        return medicationCode;
    }

    public String getReaction() {
        return reaction;
    }

    public AllergySeverity getSeverity() {
        return severity;
    }

    public AllergyStatus getStatus() {
        return status;
    }

    public UUID getRecordedByProfessionalPublicId() {
        return recordedByProfessionalPublicId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
