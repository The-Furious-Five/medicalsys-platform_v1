package com.medicalsys.identidad.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Accion autorizable sobre un recurso de la plataforma. */
@Entity
@Table(name = "permission")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 30)
    private PermissionModule module;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "is_sensitive", nullable = false)
    private boolean sensitive;

    /** Clasificacion MAC (ISO-01): solo la reciben las cuentas con un nivel que la cubra. */
    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 20)
    private SecurityLevel classification = SecurityLevel.INTERNAL;

    protected Permission() {
    }

    /** El catalogo lo escriben las migraciones; la API solo asigna permisos existentes a roles. */
    public Permission(String code, PermissionModule module, String description, boolean sensitive) {
        this.code = code;
        this.module = module;
        this.description = description;
        this.sensitive = sensitive;
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public PermissionModule getModule() {
        return module;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public SecurityLevel getClassification() {
        return classification;
    }

    public void setClassification(SecurityLevel classification) {
        this.classification = classification;
    }
}
