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
    private Integer id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    protected Specialty() {}

    public Specialty(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Integer getId()    { return id; }
    public String  getCode()  { return code; }
    public String  getName()  { return name; }
    public boolean isActive() { return active; }
}
