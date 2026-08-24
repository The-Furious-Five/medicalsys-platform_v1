package com.medicalsys.catalogos.studytype;

import jakarta.persistence.*;

/**
 * Tipo de estudio/examen médico.
 * Tabla: study_type (catalogs_db)
 * El code 'X_RAY' es consumido por ms-historial.
 */
@Entity
@Table(name = "study_type")
public class StudyType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(name = "requires_fasting", nullable = false)
    private boolean requiresFasting = false;

    @Column(name = "generates_image", nullable = false)
    private boolean generatesImage = false;

    @Column(name = "result_unit", length = 30)
    private String resultUnit;

    @Column(name = "reference_min", precision = 12, scale = 4)
    private java.math.BigDecimal referenceMin;

    @Column(name = "reference_max", precision = 12, scale = 4)
    private java.math.BigDecimal referenceMax;

    @Column(nullable = false)
    private boolean active = true;

    public enum Category {
        LABORATORY, IMAGING, PATHOLOGY, CARDIOLOGY, ENDOSCOPY, OTHER
    }

    protected StudyType() {}

    public Short    getId()              { return id; }
    public String   getCode()            { return code; }
    public String   getName()            { return name; }
    public Category getCategory()        { return category; }
    public boolean  isRequiresFasting()  { return requiresFasting; }
    public boolean  isGeneratesImage()   { return generatesImage; }
    public String   getResultUnit()      { return resultUnit; }
    public java.math.BigDecimal getReferenceMin() { return referenceMin; }
    public java.math.BigDecimal getReferenceMax() { return referenceMax; }
    public boolean  isActive()           { return active; }
}
