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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Regla semanal de disponibilidad de un profesional dentro de una organizacion. */
@Entity
@Table(name = "schedule_availability")
public class ScheduleAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "professional_organization_public_id", nullable = false)
    private UUID professionalOrganizationPublicId;

    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_duration_minutes", nullable = false)
    private Short slotDurationMinutes = 30;

    @Column(name = "simultaneous_capacity", nullable = false)
    private Short simultaneousCapacity = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false, length = 20)
    private AvailabilityModality modality = AvailabilityModality.IN_PERSON;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_resource_id")
    private PhysicalResource preferredResource;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean coversDate(LocalDate date) {
        return active
                && dayOfWeek == date.getDayOfWeek().getValue()
                && !validFrom.isAfter(date)
                && (validUntil == null || !validUntil.isBefore(date));
    }

    public Long getId() {
        return id;
    }

    public UUID getProfessionalOrganizationPublicId() {
        return professionalOrganizationPublicId;
    }

    public void setProfessionalOrganizationPublicId(UUID professionalOrganizationPublicId) {
        this.professionalOrganizationPublicId = professionalOrganizationPublicId;
    }

    public Short getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Short dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Short getSlotDurationMinutes() {
        return slotDurationMinutes;
    }

    public void setSlotDurationMinutes(Short slotDurationMinutes) {
        this.slotDurationMinutes = slotDurationMinutes;
    }

    public Short getSimultaneousCapacity() {
        return simultaneousCapacity;
    }

    public void setSimultaneousCapacity(Short simultaneousCapacity) {
        this.simultaneousCapacity = simultaneousCapacity;
    }

    public AvailabilityModality getModality() {
        return modality;
    }

    public void setModality(AvailabilityModality modality) {
        this.modality = modality;
    }

    public PhysicalResource getPreferredResource() {
        return preferredResource;
    }

    public void setPreferredResource(PhysicalResource preferredResource) {
        this.preferredResource = preferredResource;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
