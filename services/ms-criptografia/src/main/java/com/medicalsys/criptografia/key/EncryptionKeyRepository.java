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

    /** Clave global de plataforma (organization_public_id IS NULL). */
    Optional<EncryptionKey> findFirstByOrganizationPublicIdIsNullAndPurposeAndStatus(
            EncryptionKey.Purpose purpose,
            EncryptionKey.Status status);
}
