package com.medicalsys.criptografia.key;

import com.medicalsys.criptografia.usage.KeyUsage;
import com.medicalsys.criptografia.usage.KeyUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Servicio de operaciones criptográficas.
 *
 * DEMO MVP: usa AES-256-GCM con una clave derivada de DEMO_MASTER_KEY.
 * En producción se usará Vault/KMS. No versionar DEMO_MASTER_KEY con valor.
 *
 * REGLA: Este servicio nunca devuelve la clave maestra ni material de clave.
 */
@Service
@Transactional(readOnly = true)
public class EncryptionKeyService {

    private final EncryptionKeyRepository keyRepository;
    private final KeyUsageRepository      usageRepository;

    @Value("${DEMO_MASTER_KEY}")
    private String demoMasterKey;

    public EncryptionKeyService(EncryptionKeyRepository keyRepository,
                                KeyUsageRepository usageRepository) {
        this.keyRepository  = keyRepository;
        this.usageRepository = usageRepository;
    }

    /**
     * Devuelve los metadatos de la clave activa para el tenant.
     * organizationPublicId == null → clave global de plataforma.
     * NUNCA devuelve kek_reference ni wrapped_dek.
     */
    public EncryptionKeyDto findActiveKey(byte[] organizationPublicId,
                                          EncryptionKey.Purpose purpose) {
        EncryptionKey key = resolveActiveKey(organizationPublicId, purpose);
        return EncryptionKeyDto.from(key);
    }

    /**
     * Cifra texto plano con la clave activa del tenant.
     * Devuelve texto Base64.
     * Registra el uso en key_usage.
     */
    @Transactional
    public String encrypt(byte[] organizationPublicId,
                          EncryptionKey.Purpose purpose,
                          String plaintext,
                          String requestingService,
                          UUID correlationId) {

        if (plaintext == null || plaintext.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLAINTEXT_REQUIRED");
        }
        if (requestingService == null || requestingService.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REQUESTING_SERVICE_REQUIRED");
        }
        if (correlationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CORRELATION_ID_REQUIRED");
        }
        EncryptionKey key = resolveActiveKey(organizationPublicId, purpose);
        String ciphertext = encryptAesGcm(plaintext);
        registerUsage(key, KeyUsage.Operation.ENCRYPT, requestingService,
                "TEXT", null, correlationId, true, null);
        return ciphertext;
    }

    /**
     * Descifra texto cifrado con la clave activa del tenant.
     * Registra el uso en key_usage.
     */
    @Transactional
    public String decrypt(byte[] organizationPublicId,
                          EncryptionKey.Purpose purpose,
                          String ciphertext,
                          String requestingService,
                          UUID correlationId) {

        EncryptionKey key = resolveActiveKey(organizationPublicId, purpose);
        String plaintext;
        try {
            plaintext = decryptAesGcm(ciphertext);
            registerUsage(key, KeyUsage.Operation.DECRYPT, requestingService,
                    "TEXT", null, correlationId, true, null);
        } catch (Exception e) {
            registerUsage(key, KeyUsage.Operation.DECRYPT, requestingService,
                    "TEXT", null, correlationId, false, "DECRYPT_FAILED");
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "DECRYPT_FAILED");
        }
        return plaintext;
    }

    // ----------------------------------------------------------------
    // Helpers privados
    // ----------------------------------------------------------------

    private EncryptionKey resolveActiveKey(byte[] organizationPublicId,
                                           EncryptionKey.Purpose purpose) {
        if (organizationPublicId == null) {
            return keyRepository
                    .findFirstByOrganizationPublicIdIsNullAndPurposeAndStatus(
                            purpose, EncryptionKey.Status.ACTIVE)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "ACTIVE_KEY_NOT_FOUND"));
        }
        return keyRepository
                .findFirstByOrganizationPublicIdAndPurposeAndStatus(
                        organizationPublicId, purpose, EncryptionKey.Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "ACTIVE_KEY_NOT_FOUND"));
    }

    private String encryptAesGcm(String plaintext) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo inicializar AES-GCM", ex);
        }
    }

    private String decryptAesGcm(String ciphertext) throws GeneralSecurityException {
        byte[] payload = Base64.getDecoder().decode(ciphertext);
        if (payload.length <= 28) {
            throw new GeneralSecurityException("Ciphertext AES-GCM inválido");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] nonce = new byte[12];
        buffer.get(nonce);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(128, nonce));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKey aesKey() throws GeneralSecurityException {
        byte[] key = MessageDigest.getInstance("SHA-256")
                .digest(demoMasterKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

    @Transactional
    void registerUsage(EncryptionKey key,
                       KeyUsage.Operation operation,
                       String requestingService,
                       String subjectType,
                       byte[] subjectPublicId,
                       UUID correlationId,
                       boolean success,
                       String errorCode) {
        KeyUsage usage = new KeyUsage(
                key, operation, requestingService,
                subjectType, subjectPublicId,
                uuidToBytes(correlationId),
                success ? KeyUsage.Result.SUCCESS : KeyUsage.Result.FAILURE,
                errorCode,
                Instant.now()
        );
        usageRepository.save(usage);
    }

    private byte[] uuidToBytes(UUID uuid) {
        if (uuid == null) return null;
        String hex = uuid.toString().replace("-", "");
        return HexFormat.of().parseHex(hex);
    }
}
