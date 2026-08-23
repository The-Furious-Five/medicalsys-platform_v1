package com.medicalsys.identidad.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;

/** Rol de autorizacion definido por la plataforma o por un tenant. */
@Entity
@Table(name = "role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 30)
    private RoleCode code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private RoleScope scope;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @OrderBy("code asc")
    private Set<Permission> permissions = new LinkedHashSet<>();

    public Short getId() {
        return id;
    }

    public RoleCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public RoleScope getScope() {
        return scope;
    }

    public boolean isSystem() {
        return system;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
