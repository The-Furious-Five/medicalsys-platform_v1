package com.medicalsys.historial.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Medicion estructurada de signos vitales ({@code vital_sign}).
 *
 * <p>El indice de masa corporal es una columna generada por la base a partir de peso y talla: no se
 * calcula en Java para evitar dos verdades sobre el mismo dato.
 */
@Entity
@Table(name = "vital_sign")
public class VitalSign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinical_encounter_id", nullable = false, updatable = false)
    private ClinicalEncounter clinicalEncounter;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "systolic_pressure", columnDefinition = "SMALLINT UNSIGNED")
    private Integer systolicPressure;

    @Column(name = "diastolic_pressure", columnDefinition = "SMALLINT UNSIGNED")
    private Integer diastolicPressure;

    @Column(name = "heart_rate", columnDefinition = "SMALLINT UNSIGNED")
    private Integer heartRate;

    @Column(name = "respiratory_rate", columnDefinition = "SMALLINT UNSIGNED")
    private Integer respiratoryRate;

    @Column(name = "temperature_c", precision = 4, scale = 1)
    private BigDecimal temperatureC;

    @Column(name = "oxygen_saturation", columnDefinition = "TINYINT UNSIGNED")
    private Integer oxygenSaturation;

    @Column(name = "blood_glucose_mg_dl", columnDefinition = "SMALLINT UNSIGNED")
    private Integer bloodGlucoseMgDl;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "bmi", insertable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal bmi;

    @Column(name = "abdominal_circumference_cm", precision = 5, scale = 1)
    private BigDecimal abdominalCircumferenceCm;

    @Column(name = "pain_scale", columnDefinition = "TINYINT UNSIGNED")
    private Integer painScale;

    @Column(name = "recorded_by_user_public_id", columnDefinition = "BINARY(16)")
    private UUID recordedByUserPublicId;

    protected VitalSign() {
        // Requerido por JPA.
    }

    /** Constructor de la medicion; los valores ausentes quedan nulos y no se inventan. */
    public static Builder builder(ClinicalEncounter encounter, Instant measuredAt) {
        return new Builder(encounter, measuredAt);
    }

    public Long getId() {
        return id;
    }

    public ClinicalEncounter getClinicalEncounter() {
        return clinicalEncounter;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public Integer getSystolicPressure() {
        return systolicPressure;
    }

    public Integer getDiastolicPressure() {
        return diastolicPressure;
    }

    public Integer getHeartRate() {
        return heartRate;
    }

    public Integer getRespiratoryRate() {
        return respiratoryRate;
    }

    public BigDecimal getTemperatureC() {
        return temperatureC;
    }

    public Integer getOxygenSaturation() {
        return oxygenSaturation;
    }

    public Integer getBloodGlucoseMgDl() {
        return bloodGlucoseMgDl;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public BigDecimal getBmi() {
        return bmi;
    }

    public BigDecimal getAbdominalCircumferenceCm() {
        return abdominalCircumferenceCm;
    }

    public Integer getPainScale() {
        return painScale;
    }

    public UUID getRecordedByUserPublicId() {
        return recordedByUserPublicId;
    }

    /** Constructor fluido: la tabla tiene catorce mediciones opcionales. */
    public static final class Builder {

        private final VitalSign instance = new VitalSign();

        private Builder(ClinicalEncounter encounter, Instant measuredAt) {
            instance.clinicalEncounter = Objects.requireNonNull(encounter, "clinicalEncounter");
            instance.measuredAt = Objects.requireNonNull(measuredAt, "measuredAt");
        }

        public Builder pressure(Integer systolic, Integer diastolic) {
            instance.systolicPressure = systolic;
            instance.diastolicPressure = diastolic;
            return this;
        }

        public Builder rates(Integer heartRate, Integer respiratoryRate) {
            instance.heartRate = heartRate;
            instance.respiratoryRate = respiratoryRate;
            return this;
        }

        public Builder temperature(BigDecimal temperatureC) {
            instance.temperatureC = temperatureC;
            return this;
        }

        public Builder oxygenSaturation(Integer oxygenSaturation) {
            instance.oxygenSaturation = oxygenSaturation;
            return this;
        }

        public Builder bloodGlucose(Integer bloodGlucoseMgDl) {
            instance.bloodGlucoseMgDl = bloodGlucoseMgDl;
            return this;
        }

        public Builder anthropometry(BigDecimal weightKg, BigDecimal heightCm, BigDecimal abdominalCircumferenceCm) {
            instance.weightKg = weightKg;
            instance.heightCm = heightCm;
            instance.abdominalCircumferenceCm = abdominalCircumferenceCm;
            return this;
        }

        public Builder painScale(Integer painScale) {
            instance.painScale = painScale;
            return this;
        }

        public Builder recordedBy(UUID userPublicId) {
            instance.recordedByUserPublicId = userPublicId;
            return this;
        }

        public VitalSign build() {
            return instance;
        }
    }
}
