package com.medicalsys.agenda.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Consultorio, sala o equipo reservable en una sucursal. */
@Entity
@Table(name = "physical_resource")
public class PhysicalResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "organization_public_id", nullable = false)
    private UUID organizationPublicId;

    @Column(name = "branch_public_id", nullable = false)
    private UUID branchPublicId;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ResourceType type;

    @Column(name = "floor", length = 20)
    private String floor;

    @Column(name = "people_capacity", nullable = false)
    private Short peopleCapacity = 1;

    @Column(name = "preparation_minutes", nullable = false)
    private Short preparationMinutes = 0;

    @Column(name = "requires_sterilization", nullable = false)
    private boolean requiresSterilization;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equipment")
    private String equipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResourceStatus status = ResourceStatus.AVAILABLE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public boolean isBookable() {
        return status == ResourceStatus.AVAILABLE || status == ResourceStatus.OCCUPIED;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public void setOrganizationPublicId(UUID organizationPublicId) {
        this.organizationPublicId = organizationPublicId;
    }

    public UUID getBranchPublicId() {
        return branchPublicId;
    }

    public void setBranchPublicId(UUID branchPublicId) {
        this.branchPublicId = branchPublicId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ResourceType getType() {
        return type;
    }

    public void setType(ResourceType type) {
        this.type = type;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public Short getPeopleCapacity() {
        return peopleCapacity;
    }

    public void setPeopleCapacity(Short peopleCapacity) {
        this.peopleCapacity = peopleCapacity;
    }

    public Short getPreparationMinutes() {
        return preparationMinutes;
    }

    public void setPreparationMinutes(Short preparationMinutes) {
        this.preparationMinutes = preparationMinutes;
    }

    public boolean isRequiresSterilization() {
        return requiresSterilization;
    }

    public void setRequiresSterilization(boolean requiresSterilization) {
        this.requiresSterilization = requiresSterilization;
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public ResourceStatus getStatus() {
        return status;
    }

    public void setStatus(ResourceStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
