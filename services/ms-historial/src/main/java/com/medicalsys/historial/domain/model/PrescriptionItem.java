package com.medicalsys.historial.domain.model;

import com.medicalsys.historial.domain.enums.AdministrationRoute;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Medicamento, dosis, frecuencia e indicaciones de la receta ({@code prescription_item}).
 *
 * <p>Debe traer un codigo del vademecum o una descripcion libre: una receta magistral no tiene
 * codigo, pero nunca puede quedar sin identificar el producto.
 */
@Entity
@Table(name = "prescription_item")
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false, updatable = false)
    private Prescription prescription;

    @Column(name = "item_number", nullable = false, updatable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer itemNumber;

    @Column(name = "medication_code", length = 30)
    private String medicationCode;

    @Column(name = "free_text_description", length = 200)
    private String freeTextDescription;

    @Column(name = "dose", nullable = false, length = 80)
    private String dose;

    @Enumerated(EnumType.STRING)
    @Column(name = "administration_route", nullable = false)
    private AdministrationRoute administrationRoute;

    @Column(name = "frequency_hours", columnDefinition = "SMALLINT UNSIGNED")
    private Integer frequencyHours;

    @Column(name = "duration_days", columnDefinition = "SMALLINT UNSIGNED")
    private Integer durationDays;

    @Column(name = "quantity", nullable = false, precision = 8, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "encrypted_instructions", length = 2048)
    private byte[] encryptedInstructions;

    protected PrescriptionItem() {
        // Requerido por JPA.
    }

    static PrescriptionItem of(Prescription prescription, int itemNumber, String medicationCode,
            String freeTextDescription, String dose, AdministrationRoute route, Integer frequencyHours,
            Integer durationDays, BigDecimal quantity, String unit, byte[] encryptedInstructions) {
        PrescriptionItem item = new PrescriptionItem();
        item.prescription = Objects.requireNonNull(prescription, "prescription");
        item.itemNumber = itemNumber;
        item.medicationCode = medicationCode;
        item.freeTextDescription = freeTextDescription;
        item.dose = Objects.requireNonNull(dose, "dose");
        item.administrationRoute = route == null ? AdministrationRoute.ORAL : route;
        item.frequencyHours = frequencyHours;
        item.durationDays = durationDays;
        item.quantity = quantity == null ? BigDecimal.ONE : quantity;
        item.unit = unit;
        item.encryptedInstructions = encryptedInstructions;
        return item;
    }

    public Long getId() {
        return id;
    }

    public Prescription getPrescription() {
        return prescription;
    }

    public Integer getItemNumber() {
        return itemNumber;
    }

    public String getMedicationCode() {
        return medicationCode;
    }

    public String getFreeTextDescription() {
        return freeTextDescription;
    }

    public String getDose() {
        return dose;
    }

    public AdministrationRoute getAdministrationRoute() {
        return administrationRoute;
    }

    public Integer getFrequencyHours() {
        return frequencyHours;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public byte[] getEncryptedInstructions() {
        return encryptedInstructions;
    }
}
