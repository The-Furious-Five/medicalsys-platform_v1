package com.medicalsys.catalogos.specialty;

import jakarta.persistence.*;

/**
 * Especialidad médica.
 * Tabla: specialty (catalogs_db)
 */
@Entity
@Table(name = "specialty")
public class Specialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    protected Specialty() {}

    public Specialty(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Short   getId()    { return id; }
    public String  getCode()  { return code; }
    public String  getName()  { return name; }
    public String  getDescription() { return description; }
    public boolean isActive() { return active; }
}
