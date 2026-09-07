package com.medicalsys.catalogos.medication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entrada del vademecum.
 * Tabla: medication (catalogs_db)
 */
@Entity
@Table(name = "medication")
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "generic_name", nullable = false, length = 160)
    private String genericName;

    @Column(name = "trade_name", length = 160)
    private String tradeName;

    @Column(length = 60)
    private String concentration;

    @Enumerated(EnumType.STRING)
    @Column(name = "pharmaceutical_form", nullable = false, length = 20)
    private PharmaceuticalForm pharmaceuticalForm = PharmaceuticalForm.TABLET;

    @Enumerated(EnumType.STRING)
    @Column(name = "administration_route", nullable = false, length = 20)
    private AdministrationRoute administrationRoute = AdministrationRoute.ORAL;

    @Column(name = "is_controlled", nullable = false)
    private boolean controlled = false;

    @Column(name = "requires_prescription", nullable = false)
    private boolean requiresPrescription = true;

    @Column(nullable = false)
    private boolean active = true;

    public enum PharmaceuticalForm {
        TABLET, CAPSULE, SYRUP, SUSPENSION, INJECTABLE, CREAM, OINTMENT, DROPS,
        SUPPOSITORY, PATCH, AEROSOL, OTHER
    }

    public enum AdministrationRoute {
        ORAL, INTRAVENOUS, INTRAMUSCULAR, SUBCUTANEOUS, TOPICAL, OPHTHALMIC, OTIC,
        NASAL, RECTAL, INHALATION, OTHER
    }

    protected Medication() {
    }

    public Medication(String code, String genericName, boolean active) {
        this.code = code;
        this.genericName = genericName;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getGenericName() {
        return genericName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getConcentration() {
        return concentration;
    }

    public PharmaceuticalForm getPharmaceuticalForm() {
        return pharmaceuticalForm;
    }

    public AdministrationRoute getAdministrationRoute() {
        return administrationRoute;
    }

    public boolean isControlled() {
        return controlled;
    }

    public boolean isRequiresPrescription() {
        return requiresPrescription;
    }

    public boolean isActive() {
        return active;
    }
}
