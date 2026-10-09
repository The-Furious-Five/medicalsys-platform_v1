package com.medicalsys.pacientes.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Evidencia inmutable de que una identidad duplicada se unificó con otra. */
@Entity
@Table(name = "patient_merge", uniqueConstraints = {
        @UniqueConstraint(name = "uq_patient_merge_merged_patient_id", columnNames = "merged_patient_id"),
        @UniqueConstraint(name = "uq_patient_merge_event_public_id", columnNames = "event_public_id")
})
public class PatientMerge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "surviving_patient_id", nullable = false)
    private Patient survivingPatient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merged_patient_id", nullable = false)
    private Patient mergedPatient;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /** Referencia externa a identity_db.user_account.public_id; deliberadamente sin FK. */
    @Column(name = "merged_by_user_public_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID mergedByUserPublicId;

    @Generated(event = EventType.INSERT)
    @Column(name = "merged_at", insertable = false, updatable = false)
    private Instant mergedAt;

    @Column(name = "event_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID eventPublicId;

    protected PatientMerge() {
        // Requerido por JPA.
    }

    public static PatientMerge create(Patient survivingPatient, Patient mergedPatient, String reason,
            UUID mergedByUserPublicId, UUID eventPublicId) {
        if (Objects.equals(survivingPatient.getPublicId(), mergedPatient.getPublicId())) {
            throw new IllegalArgumentException("Los pacientes de una fusion deben ser diferentes");
        }
        PatientMerge merge = new PatientMerge();
        merge.survivingPatient = Objects.requireNonNull(survivingPatient, "survivingPatient");
        merge.mergedPatient = Objects.requireNonNull(mergedPatient, "mergedPatient");
        merge.reason = requiredReason(reason);
        merge.mergedByUserPublicId = Objects.requireNonNull(mergedByUserPublicId, "mergedByUserPublicId");
        merge.eventPublicId = Objects.requireNonNull(eventPublicId, "eventPublicId");
        return merge;
    }

    private static String requiredReason(String reason) {
        String normalized = Objects.requireNonNull(reason, "reason").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("reason no puede estar vacio");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public Patient getSurvivingPatient() { return survivingPatient; }
    public Patient getMergedPatient() { return mergedPatient; }
    public String getReason() { return reason; }
    public UUID getMergedByUserPublicId() { return mergedByUserPublicId; }
    public Instant getMergedAt() { return mergedAt; }
    public UUID getEventPublicId() { return eventPublicId; }
}
