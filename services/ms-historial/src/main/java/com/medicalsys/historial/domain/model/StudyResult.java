package com.medicalsys.historial.domain.model;

import com.medicalsys.historial.domain.enums.StudyResultStatus;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Resultado clinico y referencia al documento del estudio ({@code study_result}).
 *
 * <p>ms-historial no guarda binarios: {@code document_public_id} apunta al objeto administrado por
 * ms-documental en MinIO. El informe narrativo se persiste cifrado.
 */
@Entity
@Table(name = "study_result")
public class StudyResult {

    /** Parametro por defecto cuando el estudio entrega un unico resultado, como una placa. */
    public static final String DEFAULT_PARAMETER = "RESULT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_order_id", nullable = false, updatable = false)
    private StudyOrder studyOrder;

    @Column(name = "parameter", nullable = false, updatable = false, length = 120)
    private String parameter;

    @Column(name = "numeric_value", precision = 14, scale = 4)
    private BigDecimal numericValue;

    @Column(name = "encrypted_text_value", length = 4096)
    private byte[] encryptedTextValue;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "reference_min", precision = 14, scale = 4)
    private BigDecimal referenceMin;

    @Column(name = "reference_max", precision = 14, scale = 4)
    private BigDecimal referenceMax;

    @Column(name = "out_of_range", nullable = false)
    private boolean outOfRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudyResultStatus status;

    @Column(name = "document_public_id", columnDefinition = "BINARY(16)")
    private UUID documentPublicId;

    @Column(name = "reported_by_professional_public_id", columnDefinition = "BINARY(16)")
    private UUID reportedByProfessionalPublicId;

    @Column(name = "reported_at")
    private Instant reportedAt;

    @Column(name = "delivered_to_patient_at")
    private Instant deliveredToPatientAt;

    @Column(name = "encryption_key_public_id", columnDefinition = "BINARY(16)")
    private UUID encryptionKeyPublicId;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected StudyResult() {
        // Requerido por JPA.
    }

    /**
     * Registra un resultado informado.
     *
     * <p>El indicador {@code out_of_range} se deriva del valor y sus limites: es un calculo de la
     * capa de servicio, no un dato que el cliente pueda contradecir.
     */
    public static StudyResult report(StudyOrder order, String parameter, BigDecimal numericValue,
            byte[] encryptedTextValue, String unit, BigDecimal referenceMin, BigDecimal referenceMax,
            StudyResultStatus status, UUID documentPublicId, UUID reportedByProfessionalPublicId,
            UUID encryptionKeyPublicId) {
        StudyResult result = new StudyResult();
        result.studyOrder = Objects.requireNonNull(order, "studyOrder");
        result.parameter = parameter == null || parameter.isBlank() ? DEFAULT_PARAMETER : parameter;
        result.numericValue = numericValue;
        result.encryptedTextValue = encryptedTextValue;
        result.unit = unit;
        result.referenceMin = referenceMin;
        result.referenceMax = referenceMax;
        result.status = status == null ? StudyResultStatus.PRELIMINARY : status;
        result.documentPublicId = documentPublicId;
        result.reportedByProfessionalPublicId = reportedByProfessionalPublicId;
        result.encryptionKeyPublicId = encryptionKeyPublicId;
        result.reportedAt = Instant.now();
        result.outOfRange = computeOutOfRange(numericValue, referenceMin, referenceMax);
        return result;
    }

    /** Registra la entrega del resultado al paciente. */
    public void markDeliveredToPatient() {
        this.deliveredToPatientAt = Instant.now();
    }

    private static boolean computeOutOfRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) {
            return false;
        }
        boolean belowMin = min != null && value.compareTo(min) < 0;
        boolean aboveMax = max != null && value.compareTo(max) > 0;
        return belowMin || aboveMax;
    }

    public Long getId() {
        return id;
    }

    public StudyOrder getStudyOrder() {
        return studyOrder;
    }

    public String getParameter() {
        return parameter;
    }

    public BigDecimal getNumericValue() {
        return numericValue;
    }

    public byte[] getEncryptedTextValue() {
        return encryptedTextValue;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getReferenceMin() {
        return referenceMin;
    }

    public BigDecimal getReferenceMax() {
        return referenceMax;
    }

    public boolean isOutOfRange() {
        return outOfRange;
    }

    public StudyResultStatus getStatus() {
        return status;
    }

    public UUID getDocumentPublicId() {
        return documentPublicId;
    }

    public UUID getReportedByProfessionalPublicId() {
        return reportedByProfessionalPublicId;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public Instant getDeliveredToPatientAt() {
        return deliveredToPatientAt;
    }

    public UUID getEncryptionKeyPublicId() {
        return encryptionKeyPublicId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
