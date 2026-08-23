package com.medicalsys.agenda.domain;

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
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Historial inmutable de cambios de estado de una cita. */
@Entity
@Table(name = "appointment_status_history")
public class AppointmentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private AppointmentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private AppointmentStatus newStatus;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "user_account_public_id")
    private UUID userAccountPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private StatusChangeOrigin origin = StatusChangeOrigin.USER;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    protected AppointmentStatusHistory() {
    }

    public AppointmentStatusHistory(Appointment appointment, AppointmentStatus previousStatus,
                                    AppointmentStatus newStatus, String reason, UUID userAccountPublicId,
                                    StatusChangeOrigin origin) {
        this.appointment = appointment;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.userAccountPublicId = userAccountPublicId;
        this.origin = origin == null ? StatusChangeOrigin.USER : origin;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public AppointmentStatus getPreviousStatus() {
        return previousStatus;
    }

    public AppointmentStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public UUID getUserAccountPublicId() {
        return userAccountPublicId;
    }

    public StatusChangeOrigin getOrigin() {
        return origin;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
