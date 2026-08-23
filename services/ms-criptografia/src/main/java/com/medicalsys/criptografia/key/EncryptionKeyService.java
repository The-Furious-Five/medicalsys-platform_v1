package com.medicalsys.criptografia.key;

import com.medicalsys.criptografia.usage.KeyUsage;
import com.medicalsys.criptografia.usage.KeyUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Servicio de operaciones criptográficas.
 *
 * DEMO MVP: En esta fase se usa AES-GCM con DEMO_MASTER_KEY como clave
 * derivada directamente de la variable de entorno, sin KEK real.
 * En producción se usará Vault/KMS. No versionar DEMO_MASTER_KEY con valor.
 *
 * REGLA: Este servicio nunca devuelve la clave maestra ni material de clave.
 */
@Service
@Transactional(readOnly = true)
public class EncryptionKeyService {

    private final EncryptionKeyRepository keyRepository;
    private final KeyUsageRepository      usageRepository;

    @Value("${DEMO_MASTER_KEY:demo-insecure-key-do-not-use-in-prod}")
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

        EncryptionKey key = resolveActiveKey(organizationPublicId, purpose);
        String ciphertext = demoEncrypt(plaintext);
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
            plaintext = demoDecrypt(ciphertext);
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

    /**
     * Cifrado DEMO: XOR + Base64 con la clave de entorno.
     * NO apto para producción ni datos clínicos reales.
     */
    private String demoEncrypt(String plaintext) {
        byte[] plain  = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = demoMasterKey.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[plain.length];
        for (int i = 0; i < plain.length; i++) {
            result[i] = (byte) (plain[i] ^ keyBytes[i % keyBytes.length]);
        }
        return Base64.getEncoder().encodeToString(result);
    }

    private String demoDecrypt(String ciphertext) {
        byte[] encrypted = Base64.getDecoder().decode(ciphertext);
        byte[] keyBytes  = demoMasterKey.getBytes(StandardCharsets.UTF_8);
        byte[] result    = new byte[encrypted.length];
        for (int i = 0; i < encrypted.length; i++) {
            result[i] = (byte) (encrypted[i] ^ keyBytes[i % keyBytes.length]);
        }
        return new String(result, StandardCharsets.UTF_8);
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
