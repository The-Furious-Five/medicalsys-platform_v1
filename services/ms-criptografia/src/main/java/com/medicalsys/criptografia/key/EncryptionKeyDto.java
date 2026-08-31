package com.medicalsys.criptografia.key;

import com.medicalsys.criptografia.common.UuidBinary;
import java.time.Instant;

/**
 * DTO de metadatos de clave.
 * NUNCA incluye kek_reference ni wrapped_dek.
 */
public record EncryptionKeyDto(
        String publicId,
        String purpose,
        String algorithm,
        int version,
        String status,
        Instant createdAt,
        Instant rotatedAt
) {
    public static EncryptionKeyDto from(EncryptionKey k) {
        return new EncryptionKeyDto(
                k.getPublicId() != null ? String.valueOf(UuidBinary.fromBytes(k.getPublicId())) : null,
                k.getPurpose() != null ? k.getPurpose().name() : null,
                k.getAlgorithm() != null ? k.getAlgorithm().toDbValue() : null,
                k.getVersion(),
                k.getStatus() != null ? k.getStatus().name() : null,
                k.getCreatedAt(),
                k.getRotatedAt()
        );
    }
}
