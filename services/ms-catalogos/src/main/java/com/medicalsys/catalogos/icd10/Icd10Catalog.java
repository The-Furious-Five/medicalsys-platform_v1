package com.medicalsys.catalogos.icd10;

import jakarta.persistence.*;

/**
 * Entrada del catálogo CIE-10.
 * Tabla: icd10_catalog (catalogs_db)
 * PK = code (VARCHAR), sin surrogate ID.
 */
@Entity
@Table(name = "icd10_catalog")
public class Icd10Catalog {

    @Id
    @Column(name = "code", length = 10, nullable = false)
    private String code;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(length = 120)
    private String chapter;

    @Column(length = 120)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_sex", nullable = false, length = 4)
    private ApplicableSex applicableSex = ApplicableSex.BOTH;

    @Column(nullable = false)
    private boolean active = true;

    public enum ApplicableSex { BOTH, M, F }

    protected Icd10Catalog() {}

    public String       getCode()          { return code; }
    public String       getDescription()   { return description; }
    public String       getChapter()       { return chapter; }
    public String       getCategory()      { return category; }
    public ApplicableSex getApplicableSex(){ return applicableSex; }
    public boolean      isActive()         { return active; }
}
