package com.medicalsys.criptografia.key;

import com.medicalsys.criptografia.common.UuidBinary;
import com.medicalsys.criptografia.crypto.TenantAesGcm;
import com.medicalsys.criptografia.usage.KeyUsage;
import com.medicalsys.criptografia.usage.KeyUsageAudit;
import java.security.GeneralSecurityException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Operaciones de cifrado del MVP.
 *
 * <p>La DEK efectiva se deriva por tenant, proposito y version (HKDF). El ciphertext queda
 * autenticado con AAD de ese mismo contexto. Vault/KMS queda fuera del alcance.
 */
@Service
@Transactional(readOnly = true)
public class EncryptionKeyService {

    private final EncryptionKeyRepository keyRepository;
    private final KeyUsageAudit usageAudit;
    private final String demoMasterKey;

    public EncryptionKeyService(EncryptionKeyRepository keyRepository,
                                KeyUsageAudit usageAudit,
                                @Value("${DEMO_MASTER_KEY}") String demoMasterKey) {
        this.keyRepository = keyRepository;
        this.usageAudit = usageAudit;
        this.demoMasterKey = demoMasterKey;
    }

    public EncryptionKeyDto findActiveKey(byte[] organizationPublicId, EncryptionKey.Purpose purpose) {
        return EncryptionKeyDto.from(resolveActiveKey(organizationPublicId, purpose));
    }

    @Transactional
    public CipherPayload encryptForOrganization(UUID organizationId, String plaintext,
            String requestingService, UUID correlationId) {
        requirePlaintext(plaintext);
        EncryptionKey key = resolveActiveKey(
                UuidBinary.toBytes(organizationId), EncryptionKey.Purpose.MEDICAL_RECORD);
        String ciphertext = TenantAesGcm.encrypt(demoMasterKey, key, plaintext);
        usageAudit.recordSuccess(key, KeyUsage.Operation.ENCRYPT, requestingService, correlationId);
        return new CipherPayload(UuidBinary.fromBytes(key.getPublicId()), ciphertext);
    }

    @Transactional
    public String encrypt(byte[] organizationPublicId, EncryptionKey.Purpose purpose, String plaintext,
            String requestingService, UUID correlationId) {
        requirePlaintext(plaintext);
        requireService(requestingService);
        EncryptionKey key = resolveActiveKey(organizationPublicId, purpose);
        String ciphertext = TenantAesGcm.encrypt(demoMasterKey, key, plaintext);
        usageAudit.recordSuccess(key, KeyUsage.Operation.ENCRYPT, requestingService, correlationId);
        return ciphertext;
    }

    @Transactional
    public String decryptByKeyPublicId(UUID keyPublicId, String ciphertext, String requestingService,
            UUID correlationId) {
        if (keyPublicId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CIPHERTEXT_REQUIRED");
        }
        EncryptionKey key = keyRepository.findFirstByPublicId(UuidBinary.toBytes(keyPublicId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ACTIVE_KEY_NOT_FOUND"));
        return decryptWithKey(key, ciphertext, requestingService, correlationId);
    }

    @Transactional
    public String decrypt(byte[] organizationPublicId, EncryptionKey.Purpose purpose, String ciphertext,
            String requestingService, UUID correlationId) {
        EncryptionKey key = resolveActiveKey(organizationPublicId, purpose);
        return decryptWithKey(key, ciphertext, requestingService, correlationId);
    }

    private String decryptWithKey(EncryptionKey key, String ciphertext, String requestingService,
            UUID correlationId) {
        try {
            String plaintext = TenantAesGcm.decrypt(demoMasterKey, key, ciphertext);
            usageAudit.recordSuccess(key, KeyUsage.Operation.DECRYPT, requestingService, correlationId);
            return plaintext;
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            if (key.getId() != null) {
                usageAudit.recordFailure(key.getId(), KeyUsage.Operation.DECRYPT, requestingService,
                        correlationId, "DECRYPT_FAILED");
            }
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "DECRYPT_FAILED");
        }
    }

    private EncryptionKey resolveActiveKey(byte[] organizationPublicId, EncryptionKey.Purpose purpose) {
        if (organizationPublicId != null) {
            return keyRepository
                    .findFirstByOrganizationPublicIdAndPurposeAndStatus(
                            organizationPublicId, purpose, EncryptionKey.Status.ACTIVE)
                    .or(() -> keyRepository.findFirstByOrganizationPublicIdIsNullAndPurposeAndStatus(
                            purpose, EncryptionKey.Status.ACTIVE))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ACTIVE_KEY_NOT_FOUND"));
        }
        return keyRepository
                .findFirstByOrganizationPublicIdIsNullAndPurposeAndStatus(
                        purpose, EncryptionKey.Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ACTIVE_KEY_NOT_FOUND"));
    }

    private static void requirePlaintext(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLAINTEXT_REQUIRED");
        }
    }

    private static void requireService(String requestingService) {
        if (requestingService == null || requestingService.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REQUESTING_SERVICE_REQUIRED");
        }
    }
}
