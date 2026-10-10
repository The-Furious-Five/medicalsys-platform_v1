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
import java.time.temporal.ChronoUnit;

/**
 * Par de roles que una misma cuenta no puede tener a la vez (segregacion de funciones). El par no
 * tiene orden: se guarda con el id menor en {@code role} para que (A, B) y (B, A) sean la misma fila.
 */
@Entity
@Table(name = "role_incompatibility")
public class RoleIncompatibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "incompatible_role_id", nullable = false)
    private Role incompatibleRole;

    @Column(name = "reason", nullable = false, length = 300)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RoleIncompatibility() {
    }

    /** Los dos roles deben estar ya persistidos y ordenados con {@link #ordered(Role, Role)}. */
    public RoleIncompatibility(Role role, Role incompatibleRole, String reason, UserAccount createdBy) {
        this.role = role;
        this.incompatibleRole = incompatibleRole;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /** Devuelve el par con el id menor primero, que es como lo exige el CHECK de la tabla. */
    public static Role[] ordered(Role first, Role second) {
        return first.getId() < second.getId() ? new Role[] {first, second} : new Role[] {second, first};
    }

    /** El otro rol del par, visto desde {@code side}. */
    public Role other(Role side) {
        return role.getCode().equals(side.getCode()) ? incompatibleRole : role;
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public Role getIncompatibleRole() {
        return incompatibleRole;
    }

    public String getReason() {
        return reason;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
