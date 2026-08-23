package com.medicalsys.historial.domain.model;

import com.medicalsys.historial.domain.enums.DiagnosisStatus;
import com.medicalsys.historial.domain.enums.DiagnosisType;
import com.medicalsys.historial.domain.enums.Laterality;
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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Diagnostico codificado asociado al encuentro ({@code diagnosis}).
 *
 * <p>{@code icd10_code} es una referencia a icd10_catalog de ms-catalogos: se valida por HTTP antes
 * de persistir y no existe FK entre bases.
 */
@Entity
@Table(name = "diagnosis")
public class Diagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinical_encounter_id", nullable = false, updatable = false)
    private ClinicalEncounter clinicalEncounter;

    @Column(name = "icd10_code", nullable = false, updatable = false, length = 10)
    private String icd10Code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private DiagnosisType type;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "is_chronic", nullable = false)
    private boolean chronic;

    @Enumerated(EnumType.STRING)
    @Column(name = "laterality", nullable = false)
    private Laterality laterality;

    @Column(name = "encrypted_observation", length = 2048)
    private byte[] encryptedObservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DiagnosisStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Diagnosis() {
        // Requerido por JPA.
    }

    public static Diagnosis record(ClinicalEncounter encounter, String icd10Code, DiagnosisType type,
            boolean primary, boolean chronic, Laterality laterality, byte[] encryptedObservation) {
        Diagnosis diagnosis = new Diagnosis();
        diagnosis.clinicalEncounter = Objects.requireNonNull(encounter, "clinicalEncounter");
        diagnosis.icd10Code = Objects.requireNonNull(icd10Code, "icd10Code");
        diagnosis.type = type == null ? DiagnosisType.PROVISIONAL : type;
        diagnosis.primary = primary;
        diagnosis.chronic = chronic;
        diagnosis.laterality = laterality == null ? Laterality.NOT_APPLICABLE : laterality;
        diagnosis.encryptedObservation = encryptedObservation;
        diagnosis.status = DiagnosisStatus.ACTIVE;
        return diagnosis;
    }

    /** Actualiza la evolucion del diagnostico sin borrar el registro. */
    public void evolve(DiagnosisType type, DiagnosisStatus status) {
        if (type != null) {
            this.type = type;
        }
        if (status != null) {
            this.status = status;
        }
    }

    /** Deja de ser el diagnostico principal del encuentro. */
    public void demoteFromPrimary() {
        this.primary = false;
    }

    public Long getId() {
        return id;
    }

    public ClinicalEncounter getClinicalEncounter() {
        return clinicalEncounter;
    }

    public String getIcd10Code() {
        return icd10Code;
    }

    public DiagnosisType getType() {
        return type;
    }

    public boolean isPrimary() {
        return primary;
    }

    public boolean isChronic() {
        return chronic;
    }

    public Laterality getLaterality() {
        return laterality;
    }

    public byte[] getEncryptedObservation() {
        return encryptedObservation;
    }

    public DiagnosisStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
