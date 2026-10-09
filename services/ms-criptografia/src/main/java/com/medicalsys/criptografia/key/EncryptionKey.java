package com.medicalsys.criptografia.key;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Metadatos y ciclo de vida de una clave de cifrado.
 * Tabla: encryption_key (cryptography_db)
 *
 * REGLA CRÍTICA: Esta entidad NUNCA almacena la clave maestra en claro.
 *  - kek_reference: URI/ARN que apunta a Vault o KMS externo.
 *  - wrapped_dek: DEK envuelta por la KEK (material cifrado).
 * Ningún endpoint devuelve kek_reference ni wrapped_dek.
 */
@Entity
@Table(name = "encryption_key")
public class EncryptionKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID público para referencias externas. */
    @Column(name = "public_id", nullable = false, columnDefinition = "BINARY(16)")
    private byte[] publicId;

    /** NULL = clave global de plataforma; no-NULL = clave de un tenant. */
    @Column(name = "organization_public_id", columnDefinition = "BINARY(16)")
    private byte[] organizationPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Purpose purpose;

    @Convert(converter = AlgorithmConverter.class)
    @Column(nullable = false, length = 30)
    private Algorithm algorithm = Algorithm.valueOf("AES_256_GCM");

    @Column(nullable = false)
    private short version = 1;

    /** ARN/URI de la KEK en KMS o Vault; nunca material en claro. */
    @Column(name = "kek_reference", nullable = false, length = 300)
    private String kekReference;

    /** DEK envuelta (wrapped) por la KEK. */
    @Column(name = "wrapped_dek", nullable = false, columnDefinition = "VARBINARY(512)")
    private byte[] wrappedDek;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    public enum Purpose {
        MEDICAL_RECORD, DOCUMENTS, CONTACT_DATA,
        CREDENTIALS, BILLING, DIGITAL_SIGNATURE
    }

    public enum Algorithm {
        AES_256_GCM, AES_256_CBC, CHACHA20_POLY1305;

        /** Convierte el enum a la cadena del diccionario (con guiones). */
        public String toDbValue() {
            return this.name().replace('_', '-');
        }

        public static Algorithm fromDbValue(String value) {
            return Algorithm.valueOf(value.replace('-', '_'));
        }
    }

    public enum Status { ACTIVE, AT_ROTACION, RETIRED, COMPROMISED }

    protected EncryptionKey() {}

    public EncryptionKey(byte[] publicId, byte[] organizationPublicId, Purpose purpose, short version,
            String kekReference, byte[] wrappedDek) {
        this.publicId = publicId;
        this.organizationPublicId = organizationPublicId;
        this.purpose = purpose;
        this.version = version;
        this.kekReference = kekReference;
        this.wrappedDek = wrappedDek;
        this.status = Status.ACTIVE;
        this.algorithm = Algorithm.AES_256_GCM;
        this.createdAt = Instant.now();
    }

    /* Getters */
    public Long      getId()                   { return id; }
    public byte[]    getPublicId()             { return publicId; }
    public byte[]    getOrganizationPublicId() { return organizationPublicId; }
    public Purpose   getPurpose()              { return purpose; }
    public Algorithm getAlgorithm()            { return algorithm; }
    public short     getVersion()              { return version; }
    public String    getKekReference()         { return kekReference; }
    public byte[]    getWrappedDek()           { return wrappedDek; }
    public Status    getStatus()               { return status; }
    public Instant   getCreatedAt()            { return createdAt; }
    public Instant   getRotatedAt()            { return rotatedAt; }
    public Instant   getRetiredAt()            { return retiredAt; }
}
