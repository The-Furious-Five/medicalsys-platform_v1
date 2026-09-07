package com.medicalsys.pacientes.domain.model;

import com.medicalsys.pacientes.domain.enums.ConsentSource;
import com.medicalsys.pacientes.domain.enums.NotificationCategory;
import com.medicalsys.pacientes.domain.enums.NotificationChannel;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Consentimiento de contacto del paciente para un canal y una finalidad concretos. */
@Entity
@Table(name = "notification_preference", uniqueConstraints = @UniqueConstraint(
        name = "uq_notification_preference_patient_id_channel_category",
        columnNames = {"patient_id", "channel", "category"}))
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private NotificationCategory category;

    @Column(name = "opt_in", nullable = false)
    private boolean optIn;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_source", length = 30)
    private ConsentSource consentSource;

    @Column(name = "opt_in_at")
    private Instant optInAt;

    @Column(name = "opt_out_at")
    private Instant optOutAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
        // Requerido por JPA.
    }

    public static NotificationPreference create(Patient patient, NotificationChannel channel,
            NotificationCategory category) {
        NotificationPreference preference = new NotificationPreference();
        preference.patient = Objects.requireNonNull(patient, "patient");
        preference.channel = Objects.requireNonNull(channel, "channel");
        preference.category = Objects.requireNonNull(category, "category");
        return preference;
    }

    /** Activa o revoca el consentimiento y conserva las fechas necesarias para trazabilidad. */
    public void changeConsent(boolean optIn, ConsentSource consentSource, Instant occurredAt) {
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        if (optIn) {
            this.optInAt = timestamp;
            this.optOutAt = null;
        } else if (this.optIn || this.optInAt != null) {
            this.optOutAt = timestamp;
        }
        this.optIn = optIn;
        this.consentSource = consentSource;
    }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public NotificationChannel getChannel() { return channel; }
    public NotificationCategory getCategory() { return category; }
    public boolean isOptIn() { return optIn; }
    public ConsentSource getConsentSource() { return consentSource; }
    public Instant getOptInAt() { return optInAt; }
    public Instant getOptOutAt() { return optOutAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
