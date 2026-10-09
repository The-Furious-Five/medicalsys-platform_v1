package com.medicalsys.documental.domain;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Consentimiento informado de un paciente sobre un procedimiento, con su ciclo de vida completo
 * (generado, enviado, firmado, rechazado o revocado). El PDF renderizado vive en {@code document}
 * (ms-documental mismo); este registro es el que sabe si ese PDF fue firmado y como.
 */
@Entity
@Table(
        name = "informed_consent",
        uniqueConstraints = @UniqueConstraint(name = "uq_informed_consent_public_id", columnNames = "public_id"))
public class InformedConsent {

    private static final List<InformedConsentStatus> SIGNABLE = List.of(
            InformedConsentStatus.GENERATED, InformedConsentStatus.SENT, InformedConsentStatus.VIEWED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private ConsentTemplate template;

    @Column(name = "template_version", nullable = false, updatable = false)
    private short templateVersion;

    @Column(name = "patient_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID patientPublicId;

    @Column(name = "medical_record_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID medicalRecordPublicId;

    @Column(name = "appointment_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID appointmentPublicId;

    @Column(name = "clinical_encounter_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID clinicalEncounterPublicId;

    @Column(name = "health_professional_public_id", nullable = false, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID healthProfessionalPublicId;

    @Column(name = "service_offering_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID serviceOfferingPublicId;

    @Column(name = "rendered_body_hash", nullable = false, updatable = false, length = 64)
    private String renderedBodyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InformedConsentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_method", length = 30)
    private SignatureMethod signatureMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "signer_type", nullable = false, length = 20)
    private SignerType signerType;

    @Column(name = "signing_patient_public_id", columnDefinition = "BINARY(16)")
    private UUID signingPatientPublicId;

    @Column(name = "signer_document_number", length = 25)
    private String signerDocumentNumber;

    @Column(name = "witness_name", length = 160)
    private String witnessName;

    @Column(name = "witness_document_number", length = 25)
    private String witnessDocumentNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pdf_document_id")
    private StoredDocument pdfDocument;

    @Column(name = "document_sha256_hash", length = 64)
    private String documentSha256Hash;

    @Column(name = "digital_signature")
    private byte[] digitalSignature;

    @Column(name = "digital_certificate_public_id", columnDefinition = "BINARY(16)")
    private UUID digitalCertificatePublicId;

    @Column(name = "tsa_timestamp")
    private byte[] tsaTimestamp;

    @Generated(event = EventType.INSERT)
    @Column(name = "generated_at", insertable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signature_ip")
    private byte[] signatureIp;

    @Column(name = "signature_user_agent", length = 300)
    private String signatureUserAgent;

    @Column(name = "signature_geolocation", length = 60)
    private String signatureGeolocation;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 300)
    private String revocationReason;

    @Column(name = "encryption_key_public_id", columnDefinition = "BINARY(16)")
    private UUID encryptionKeyPublicId;

    @OneToMany(mappedBy = "informedConsent", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("occurredAt ASC")
    private List<ConsentEvent> events = new ArrayList<>();

    protected InformedConsent() {
        // Requerido por JPA.
    }

    public static InformedConsent generate(UUID publicId, UUID organizationPublicId, ConsentTemplate template,
            UUID patientPublicId, UUID medicalRecordPublicId, UUID appointmentPublicId,
            UUID clinicalEncounterPublicId, UUID healthProfessionalPublicId, UUID serviceOfferingPublicId,
            String renderedBodyHash, StoredDocument pdfDocument, String documentSha256Hash) {
        InformedConsent consent = new InformedConsent();
        consent.publicId = Objects.requireNonNull(publicId, "publicId");
        consent.organizationPublicId = Objects.requireNonNull(organizationPublicId, "organizationPublicId");
        consent.template = Objects.requireNonNull(template, "template");
        consent.templateVersion = (short) template.getVersion();
        consent.patientPublicId = Objects.requireNonNull(patientPublicId, "patientPublicId");
        consent.medicalRecordPublicId = Objects.requireNonNull(medicalRecordPublicId, "medicalRecordPublicId");
        consent.appointmentPublicId = appointmentPublicId;
        consent.clinicalEncounterPublicId = clinicalEncounterPublicId;
        consent.healthProfessionalPublicId =
                Objects.requireNonNull(healthProfessionalPublicId, "healthProfessionalPublicId");
        consent.serviceOfferingPublicId = serviceOfferingPublicId;
        consent.renderedBodyHash = Objects.requireNonNull(renderedBodyHash, "renderedBodyHash");
        consent.status = InformedConsentStatus.GENERATED;
        consent.signerType = SignerType.PATIENT;
        consent.pdfDocument = Objects.requireNonNull(pdfDocument, "pdfDocument");
        consent.documentSha256Hash = Objects.requireNonNull(documentSha256Hash, "documentSha256Hash");
        return consent;
    }

    public void markSent() {
        if (status == InformedConsentStatus.GENERATED) {
            status = InformedConsentStatus.SENT;
        }
    }

    public boolean isSignable() {
        return SIGNABLE.contains(status);
    }

    /**
     * Firma el consentimiento. Replica en Java el CHECK {@code ck_informed_consent_signed_data} de
     * la migracion: un {@code SIGNED} sin fecha, metodo y hash del documento es un estado imposible
     * segun la base, asi que tampoco debe poder construirse aqui.
     */
    public void sign(SignatureMethod method, SignerType signerType, UUID signingPatientPublicId,
            String signerDocumentNumber, byte[] signatureIp, String signatureUserAgent, Instant signedAt) {
        if (!isSignable()) {
            throw new IllegalStateException(
                    "El consentimiento no admite firma en su estado actual: " + status);
        }
        this.signatureMethod = Objects.requireNonNull(method, "method");
        this.signerType = Objects.requireNonNull(signerType, "signerType");
        this.signingPatientPublicId = signingPatientPublicId;
        this.signerDocumentNumber = signerDocumentNumber;
        this.signatureIp = signatureIp;
        this.signatureUserAgent = signatureUserAgent;
        this.signedAt = Objects.requireNonNull(signedAt, "signedAt");
        // documentSha256Hash ya se fijo en generate(): el contenido no cambia entre generar y firmar.
        this.status = InformedConsentStatus.SIGNED;
    }

    public void reject(String reason) {
        if (!isSignable()) {
            throw new IllegalStateException(
                    "El consentimiento no admite rechazo en su estado actual: " + status);
        }
        this.status = InformedConsentStatus.REJECTED;
        this.revocationReason = reason;
    }

    public void revoke(String reason) {
        if (status != InformedConsentStatus.SIGNED) {
            throw new IllegalStateException("Solo un consentimiento firmado puede revocarse");
        }
        this.status = InformedConsentStatus.REVOKED;
        this.revokedAt = Instant.now();
        this.revocationReason = reason;
    }

    public void addEvent(ConsentEvent event) {
        events.add(Objects.requireNonNull(event, "event"));
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

    public ConsentTemplate getTemplate() {
        return template;
    }

    public int getTemplateVersion() {
        return templateVersion; // widen: SMALLINT UNSIGNED en la base, int en el resto del servicio
    }

    public UUID getPatientPublicId() {
        return patientPublicId;
    }

    public UUID getMedicalRecordPublicId() {
        return medicalRecordPublicId;
    }

    public UUID getAppointmentPublicId() {
        return appointmentPublicId;
    }

    public UUID getClinicalEncounterPublicId() {
        return clinicalEncounterPublicId;
    }

    public UUID getHealthProfessionalPublicId() {
        return healthProfessionalPublicId;
    }

    public UUID getServiceOfferingPublicId() {
        return serviceOfferingPublicId;
    }

    public String getRenderedBodyHash() {
        return renderedBodyHash;
    }

    public InformedConsentStatus getStatus() {
        return status;
    }

    public SignatureMethod getSignatureMethod() {
        return signatureMethod;
    }

    public SignerType getSignerType() {
        return signerType;
    }

    public UUID getSigningPatientPublicId() {
        return signingPatientPublicId;
    }

    public String getSignerDocumentNumber() {
        return signerDocumentNumber;
    }

    public String getWitnessName() {
        return witnessName;
    }

    public String getWitnessDocumentNumber() {
        return witnessDocumentNumber;
    }

    public StoredDocument getPdfDocument() {
        return pdfDocument;
    }

    public String getDocumentSha256Hash() {
        return documentSha256Hash;
    }

    public UUID getDigitalCertificatePublicId() {
        return digitalCertificatePublicId;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public String getSignatureUserAgent() {
        return signatureUserAgent;
    }

    public String getSignatureGeolocation() {
        return signatureGeolocation;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public UUID getEncryptionKeyPublicId() {
        return encryptionKeyPublicId;
    }

    public List<ConsentEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }
}
