package com.medicalsys.identidad.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Asignacion de roles a una cuenta dentro de un contexto organizacional. */
@Entity
@Table(name = "user_role")
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "organization_public_id")
    private UUID organizationPublicId;

    @Column(name = "branch_public_id")
    private UUID branchPublicId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private UserAccount assignedBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected UserRole() {
    }

    public UserRole(UserAccount userAccount, Role role, UUID organizationPublicId, UUID branchPublicId,
                    LocalDate validFrom, LocalDate validUntil, UserAccount assignedBy) {
        this.userAccount = userAccount;
        this.role = role;
        this.organizationPublicId = organizationPublicId;
        this.branchPublicId = branchPublicId;
        this.validFrom = validFrom == null ? LocalDate.now() : validFrom;
        this.validUntil = validUntil;
        this.assignedBy = assignedBy;
    }

    public boolean isActiveOn(LocalDate day) {
        return !validFrom.isAfter(day) && (validUntil == null || !validUntil.isBefore(day));
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public Role getRole() {
        return role;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public UUID getBranchPublicId() {
        return branchPublicId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public UserAccount getAssignedBy() {
        return assignedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
