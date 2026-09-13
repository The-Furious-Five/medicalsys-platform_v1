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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "document",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_document_public_id", columnNames = "public_id"),
            @UniqueConstraint(name = "uq_document_bucket_key_object", columnNames = {"bucket", "key_object"})
        })
public class StoredDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "organization_public_id", nullable = false, updatable = false,
            columnDefinition = "BINARY(16)")
    private UUID organizationPublicId;

    @Column(name = "patient_public_id", columnDefinition = "BINARY(16)")
    private UUID patientPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private DocumentType type;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 20)
    private StorageProvider storageProvider;

    @Column(name = "region", length = 40)
    private String region;

    @Column(name = "bucket", nullable = false, length = 120)
    private String bucket;

    @Column(name = "key_object", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "object_version", length = 80)
    private String objectVersion;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Enumerated(EnumType.STRING)
    @Column(name = "server_side_encryption", nullable = false, length = 20)
    private ServerSideEncryption serverSideEncryption;

    @Column(name = "encryption_key_public_id", columnDefinition = "BINARY(16)")
    private UUID encryptionKeyPublicId;

    @Column(name = "is_dicom", nullable = false)
    private boolean dicom;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "json")
    private Map<String, Object> metadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "antivirus_status", nullable = false, length = 20)
    private AntivirusStatus antivirusStatus;

    @Column(name = "uploaded_by_user_public_id", columnDefinition = "BINARY(16)")
    private UUID uploadedByUserPublicId;

    @Generated(event = EventType.INSERT)
    @Column(name = "uploaded_at", insertable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "retention_until")
    private LocalDate retentionUntil;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<DocumentLink> links = new ArrayList<>();

    protected StoredDocument() {
        // Requerido por JPA.
    }

    public static StoredDocument available(UUID publicId, UUID organizationPublicId, UUID patientPublicId,
            DocumentType type, String originalFilename, String mimeType, long sizeBytes, String bucket,
            String objectKey, String objectVersion, String sha256Hash, boolean dicom,
            Map<String, Object> metadata, UUID uploadedByUserPublicId, LocalDate retentionUntil) {
        StoredDocument document = new StoredDocument();
        document.publicId = Objects.requireNonNull(publicId, "publicId");
        document.organizationPublicId = Objects.requireNonNull(organizationPublicId, "organizationPublicId");
        document.patientPublicId = patientPublicId;
        document.type = Objects.requireNonNull(type, "type");
        document.originalFilename = Objects.requireNonNull(originalFilename, "originalFilename");
        document.mimeType = Objects.requireNonNull(mimeType, "mimeType");
        document.sizeBytes = sizeBytes;
        document.storageProvider = StorageProvider.MINIO;
        document.bucket = Objects.requireNonNull(bucket, "bucket");
        document.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        document.objectVersion = objectVersion;
        document.sha256Hash = Objects.requireNonNull(sha256Hash, "sha256Hash");
        document.serverSideEncryption = ServerSideEncryption.NONE;
        document.dicom = dicom;
        document.metadata = metadata == null || metadata.isEmpty() ? null : Map.copyOf(metadata);
        document.status = DocumentStatus.AVAILABLE;
        document.antivirusStatus = AntivirusStatus.NOT_APPLICABLE;
        document.uploadedByUserPublicId = uploadedByUserPublicId;
        document.retentionUntil = retentionUntil;
        return document;
    }

    public void classify(DocumentType type, Boolean dicom, Map<String, Object> metadata) {
        this.type = Objects.requireNonNull(type, "type");
        if (dicom != null) {
            this.dicom = dicom;
        }
        if (metadata != null) {
            this.metadata = metadata.isEmpty() ? null : Map.copyOf(metadata);
        }
    }

    public void addLink(DocumentLink link) {
        links.add(Objects.requireNonNull(link, "link"));
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

    public UUID getPatientPublicId() {
        return patientPublicId;
    }

    public DocumentType getType() {
        return type;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }

    public String getRegion() {
        return region;
    }

    public String getBucket() {
        return bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getObjectVersion() {
        return objectVersion;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public ServerSideEncryption getServerSideEncryption() {
        return serverSideEncryption;
    }

    public UUID getEncryptionKeyPublicId() {
        return encryptionKeyPublicId;
    }

    public boolean isDicom() {
        return dicom;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public AntivirusStatus getAntivirusStatus() {
        return antivirusStatus;
    }

    public UUID getUploadedByUserPublicId() {
        return uploadedByUserPublicId;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public LocalDate getRetentionUntil() {
        return retentionUntil;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public List<DocumentLink> getLinks() {
        return Collections.unmodifiableList(links);
    }
}
