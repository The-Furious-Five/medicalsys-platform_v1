package com.medicalsys.criptografia.key;

import java.time.Instant;
import java.util.HexFormat;

/**
 * DTO de metadatos de clave.
 * NUNCA incluye kek_reference ni wrapped_dek.
 * Solo metadatos operativos seguros para exponer.
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
                k.getPublicId() != null
                        ? HexFormat.of().formatHex(k.getPublicId())
                        : null,
                k.getPurpose() != null  ? k.getPurpose().name()  : null,
                k.getAlgorithm() != null ? k.getAlgorithm().toDbValue() : null,
                k.getVersion(),
                k.getStatus() != null   ? k.getStatus().name()   : null,
                k.getCreatedAt(),
                k.getRotatedAt()
        );
    }
}
