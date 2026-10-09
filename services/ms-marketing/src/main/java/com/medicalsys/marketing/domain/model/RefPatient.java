package com.medicalsys.marketing.domain.model;

import com.medicalsys.marketing.domain.enums.PatientStatus;
import com.medicalsys.marketing.domain.enums.Sex;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Replica local de {@code patients_db.patient}, <b>solo demografia</b> ({@code ref_patient}).
 *
 * <p>Enumerar lo que esta entidad tiene es menos revelador que enumerar lo que no tiene: no hay
 * diagnostico, ni alergia, ni antecedente, ni direccion cifrada, ni clave de cifrado, ni ninguna
 * referencia al expediente. Los cinco campos que si estan -- nombre, fecha de nacimiento, sexo,
 * ciudad y telefono -- son exactamente los ejes de segmentacion que la seccion 5.12 autoriza.
 *
 * <p>{@code marketingOptOut} vive aqui, y no en ms-notificaciones, porque la exclusion tiene que
 * aplicarse al materializar la audiencia: un paciente que pidio no recibir publicidad no debe
 * llegar siquiera a ser un mensaje encolado.
 */
@Entity
@Table(name = "ref_patient")
public class RefPatient {

    @Id
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "full_name", nullable = false, length = 290)
    private String fullName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false)
    private Sex sex;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "phone_e164", length = 20)
    private String phoneE164;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PatientStatus status;

    @Column(name = "marketing_opt_out", nullable = false)
    private boolean marketingOptOut;

    @Column(name = "opted_out_at")
    private Instant optedOutAt;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "synced_at", insertable = false, updatable = false)
    private Instant syncedAt;

    protected RefPatient() {
        // Requerido por JPA.
    }

    /** Crea la replica demografica de un paciente recien conocido. */
    public static RefPatient of(UUID publicId, String fullName, LocalDate birthDate, Sex sex, String city,
            String phoneE164, PatientStatus status) {
        RefPatient patient = new RefPatient();
        patient.publicId = Objects.requireNonNull(publicId, "publicId");
        patient.fullName = Objects.requireNonNull(fullName, "fullName");
        patient.birthDate = birthDate;
        patient.sex = sex == null ? Sex.UNDECLARED : sex;
        patient.city = city;
        patient.phoneE164 = phoneE164;
        patient.status = status == null ? PatientStatus.ACTIVE : status;
        patient.marketingOptOut = false;
        patient.sourceVersion = 0L;
        return patient;
    }

    /**
     * Aplica una version del propietario. Un evento mas viejo que la version aplicada se ignora.
     *
     * @return {@code true} si la replica cambio
     */
    public boolean apply(String fullName, LocalDate birthDate, Sex sex, String city, String phoneE164,
            PatientStatus status, long sourceVersion) {
        if (sourceVersion < this.sourceVersion) {
            return false;
        }
        if (fullName != null) {
            this.fullName = fullName;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (sex != null) {
            this.sex = sex;
        }
        if (city != null) {
            this.city = city;
        }
        if (phoneE164 != null) {
            this.phoneE164 = phoneE164;
        }
        if (status != null) {
            this.status = status;
        }
        this.sourceVersion = sourceVersion;
        return true;
    }

    /** Registra o revoca la exclusion comercial del paciente. */
    public void setMarketingOptOut(boolean optOut, Instant at) {
        this.marketingOptOut = optOut;
        this.optedOutAt = optOut ? at : null;
    }

    /** Indica si el paciente puede entrar en una audiencia comercial. */
    public boolean isTargetable() {
        return status.isTargetable() && !marketingOptOut;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Sex getSex() {
        return sex;
    }

    public String getCity() {
        return city;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public PatientStatus getStatus() {
        return status;
    }

    public boolean isMarketingOptOut() {
        return marketingOptOut;
    }

    public Instant getOptedOutAt() {
        return optedOutAt;
    }

    public long getSourceVersion() {
        return sourceVersion;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
