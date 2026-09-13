package com.medicalsys.criptografia.key;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EncryptionKeyRepository extends JpaRepository<EncryptionKey, Long> {

    /**
     * Busca la clave activa de un tenant para un propósito dado.
     * organization_public_id NULL = clave global de plataforma.
     */
    Optional<EncryptionKey> findFirstByOrganizationPublicIdAndPurposeAndStatus(
            byte[] organizationPublicId,
            EncryptionKey.Purpose purpose,
            EncryptionKey.Status status);

    Optional<EncryptionKey> findFirstByPublicId(byte[] publicId);

    Optional<EncryptionKey> findFirstByOrganizationPublicIdIsNullAndPurposeAndStatus(
            EncryptionKey.Purpose purpose,
            EncryptionKey.Status status);
}
