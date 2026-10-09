package com.medicalsys.criptografia.usage;

import com.medicalsys.criptografia.key.EncryptionKey;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Bitácora append-only de operaciones criptográficas.
 * Tabla: key_usage (cryptography_db)
 */
@Entity
@Table(name = "key_usage")
public class KeyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encryption_key_id", nullable = false)
    private EncryptionKey encryptionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Operation operation;

    @Column(name = "requesting_service", nullable = false, length = 80)
    private String requestingService;

    @Column(name = "subject_type", nullable = false, length = 60)
    private String subjectType;

    @Column(name = "subject_public_id", columnDefinition = "BINARY(16)")
    private byte[] subjectPublicId;

    @Column(name = "correlation_id", nullable = false, columnDefinition = "BINARY(16)")
    private byte[] correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Result result;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public enum Operation { ENCRYPT, DECRYPT, WRAP, UNWRAP, ROTATE, SIGN, VERIFY }
    public enum Result    { SUCCESS, FAILURE }

    protected KeyUsage() {}

    public KeyUsage(EncryptionKey encryptionKey,
                    Operation operation,
                    String requestingService,
                    String subjectType,
                    byte[] subjectPublicId,
                    byte[] correlationId,
                    Result result,
                    String errorCode,
                    Instant occurredAt) {
        this.encryptionKey    = encryptionKey;
        this.operation        = operation;
        this.requestingService = requestingService;
        this.subjectType      = subjectType;
        this.subjectPublicId  = subjectPublicId;
        this.correlationId    = correlationId;
        this.result           = result;
        this.errorCode        = errorCode;
        this.occurredAt       = occurredAt;
    }

    public Long          getId()                { return id; }
    public EncryptionKey getEncryptionKey()     { return encryptionKey; }
    public Operation     getOperation()         { return operation; }
    public String        getRequestingService() { return requestingService; }
    public String        getSubjectType()       { return subjectType; }
    public byte[]        getSubjectPublicId()   { return subjectPublicId; }
    public byte[]        getCorrelationId()     { return correlationId; }
    public Result        getResult()            { return result; }
    public String        getErrorCode()         { return errorCode; }
    public Instant       getOccurredAt()        { return occurredAt; }
}
