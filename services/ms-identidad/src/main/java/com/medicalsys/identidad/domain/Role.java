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

/**
 * Rol de autorizacion. Los de sistema ({@link RoleCode}) los siembran las migraciones; el resto se
 * crea desde la API de gestion de roles (RBAC-02).
 */
@Entity
@Table(name = "role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Short id;

    /** Inmutable: viaja en el claim roles del JWT y en el header X-Roles hacia los servicios. */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private RoleScope scope;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_model", nullable = false, length = 4)
    private AccessModel accessModel = AccessModel.MAC;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @OrderBy("code asc")
    private Set<Permission> permissions = new LinkedHashSet<>();

    /** Roles que quien tiene este rol puede asignar o revocar, si el rol es de organizacion. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_delegation",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "assignable_role_id"))
    @OrderBy("code asc")
    private Set<Role> assignableRoles = new LinkedHashSet<>();

    /** Rol creado desde la API: nunca es de sistema y nace activo y sin permisos. */
    public static Role custom(String code, String name, String description, RoleScope scope) {
        Role role = new Role();
        role.code = code;
        role.name = name;
        role.description = description;
        role.scope = scope;
        role.system = false;
        role.active = true;
        return role;
    }

    public boolean is(RoleCode systemCode) {
        return systemCode.name().equals(code);
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RoleScope getScope() {
        return scope;
    }

    public boolean isSystem() {
        return system;
    }

    public boolean isActive() {
        return active;
    }

    public AccessModel getAccessModel() {
        return accessModel;
    }

    public boolean permits(UserAccount user, Permission permission) {
        return accessModel != AccessModel.MAC || user.clears(permission);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public Set<Role> getAssignableRoles() {
        return assignableRoles;
    }
}
