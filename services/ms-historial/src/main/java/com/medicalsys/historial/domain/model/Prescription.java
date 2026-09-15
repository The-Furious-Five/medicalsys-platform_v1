package com.medicalsys.historial.domain.model;

import com.medicalsys.historial.domain.enums.PrescriptionStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Receta emitida por un profesional durante un encuentro ({@code prescription}).
 *
 * <p>Raiz de agregado de sus detalles: los items solo existen dentro de la receta y se eliminan con
 * ella. El PDF firmado vive en ms-documental; aqui solo se guarda su UUID y su hash.
 */
@Entity
@Table(name = "prescription")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinical_encounter_id", nullable = false, updatable = false)
    private ClinicalEncounter clinicalEncounter;

    @Column(name = "prescribed_by_professional_public_id", nullable = false, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID prescribedByProfessionalPublicId;

    @Column(name = "folio", nullable = false, updatable = false, length = 30)
    private String folio;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "validity_days", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer validityDays;

    @Column(name = "encrypted_general_instructions", length = 4096)
    private byte[] encryptedGeneralInstructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PrescriptionStatus status;

    @Column(name = "pdf_document_public_id", columnDefinition = "BINARY(16)")
    private UUID pdfDocumentPublicId;

    @Column(name = "sha256_hash", length = 64)
    private String sha256Hash;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "digital_certificate_public_id", columnDefinition = "BINARY(16)")
    private UUID digitalCertificatePublicId;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("itemNumber ASC")
    private List<PrescriptionItem> items = new ArrayList<>();

    protected Prescription() {
        // Requerido por JPA.
    }

    public static Prescription issue(ClinicalEncounter encounter, String folio, Integer validityDays,
            byte[] encryptedGeneralInstructions) {
        Prescription prescription = new Prescription();
        prescription.publicId = UUID.randomUUID();
        prescription.clinicalEncounter = Objects.requireNonNull(encounter, "clinicalEncounter");
        prescription.prescribedByProfessionalPublicId = encounter.getHealthProfessionalPublicId();
        prescription.organizationPublicId = encounter.getOrganizationPublicId();
        prescription.folio = Objects.requireNonNull(folio, "folio");
        prescription.issuedAt = Instant.now();
        prescription.validityDays = validityDays == null ? 30 : validityDays;
        prescription.encryptedGeneralInstructions = encryptedGeneralInstructions;
        prescription.status = PrescriptionStatus.ISSUED;
        return prescription;
    }

    /** Agrega un detalle asignando su numero correlativo dentro de la receta. */
    public PrescriptionItem addItem(String medicationCode, String freeTextDescription, String dose,
            com.medicalsys.historial.domain.enums.AdministrationRoute route, Integer frequencyHours,
            Integer durationDays, java.math.BigDecimal quantity, String unit, byte[] encryptedInstructions) {
        int nextNumber = items.size() + 1;
        PrescriptionItem item = PrescriptionItem.of(this, nextNumber, medicationCode, freeTextDescription, dose,
                route, frequencyHours, durationDays, quantity, unit, encryptedInstructions);
        items.add(item);
        return item;
    }

    /** Vincula el PDF generado por ms-documental. */
    public void attachDocument(UUID pdfDocumentPublicId, String sha256Hash, UUID digitalCertificatePublicId) {
        this.pdfDocumentPublicId = pdfDocumentPublicId;
        this.sha256Hash = sha256Hash;
        this.digitalCertificatePublicId = digitalCertificatePublicId;
        this.signedAt = Instant.now();
    }

    /**
     * Cambia el estado de la receta.
     *
     * @throws IllegalStateException si la transicion no esta permitida; el servicio la traduce a 422
     */
    public void changeStatus(PrescriptionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Transicion de receta no permitida: " + status + " -> " + target);
        }
        this.status = target;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public UUID getOrganizationPublicId() {
        return organizationPublicId;
    }

    public ClinicalEncounter getClinicalEncounter() {
        return clinicalEncounter;
    }

    public UUID getPrescribedByProfessionalPublicId() {
        return prescribedByProfessionalPublicId;
    }

    public String getFolio() {
        return folio;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public byte[] getEncryptedGeneralInstructions() {
        return encryptedGeneralInstructions;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }

    public UUID getPdfDocumentPublicId() {
        return pdfDocumentPublicId;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public UUID getDigitalCertificatePublicId() {
        return digitalCertificatePublicId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PrescriptionItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
