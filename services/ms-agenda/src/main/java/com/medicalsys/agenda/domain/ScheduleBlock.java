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

/** Intervalo que impide reservas por ausencia, feriado u otra causa. */
@Entity
@Table(name = "schedule_block")
public class ScheduleBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "organization_public_id", nullable = false)
    private UUID organizationPublicId;

    @Column(name = "health_professional_public_id")
    private UUID healthProfessionalPublicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "physical_resource_id")
    private PhysicalResource physicalResource;

    @Column(name = "branch_public_id")
    private UUID branchPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private BlockType type;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(name = "reason", length = 250)
    private String reason;

    @Column(name = "created_by_user_public_id")
    private UUID createdByUserPublicId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean overlaps(OffsetDateTime from, OffsetDateTime to) {
        return startsAt.isBefore(to) && endsAt.isAfter(from);
    }

    public Long getId() {
        return id;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public void setOrganizationPublicId(UUID organizationPublicId) {
        this.organizationPublicId = organizationPublicId;
    }

    public UUID getHealthProfessionalPublicId() {
        return healthProfessionalPublicId;
    }

    public void setHealthProfessionalPublicId(UUID healthProfessionalPublicId) {
        this.healthProfessionalPublicId = healthProfessionalPublicId;
    }

    public PhysicalResource getPhysicalResource() {
        return physicalResource;
    }

    public void setPhysicalResource(PhysicalResource physicalResource) {
        this.physicalResource = physicalResource;
    }

    public UUID getBranchPublicId() {
        return branchPublicId;
    }

    public void setBranchPublicId(UUID branchPublicId) {
        this.branchPublicId = branchPublicId;
    }

    public BlockType getType() {
        return type;
    }

    public void setType(BlockType type) {
        this.type = type;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(OffsetDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(OffsetDateTime endsAt) {
        this.endsAt = endsAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getCreatedByUserPublicId() {
        return createdByUserPublicId;
    }

    public void setCreatedByUserPublicId(UUID createdByUserPublicId) {
        this.createdByUserPublicId = createdByUserPublicId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
