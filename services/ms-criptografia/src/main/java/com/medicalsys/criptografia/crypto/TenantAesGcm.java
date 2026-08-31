package com.medicalsys.criptografia.crypto;

import com.medicalsys.criptografia.key.EncryptionKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM con DEK derivada por tenant/version (HKDF-SHA256) y AAD de contexto.
 *
 * <p>El MVP no desenvuelve {@code wrapped_dek}: usa {@code DEMO_MASTER_KEY} como IKM.
 * Produccion debe sustituir esto por Vault/KMS.
 */
public final class TenantAesGcm {

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] HKDF_SALT = "MedicalSys-MVP-HKDF".getBytes(StandardCharsets.UTF_8);

    private TenantAesGcm() {
    }

    public static String encrypt(String masterKey, EncryptionKey key, String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, dek(masterKey, key), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(key));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar con AES-GCM", ex);
        }
    }

    public static String decrypt(String masterKey, EncryptionKey key, String ciphertext)
            throws GeneralSecurityException {
        byte[] payload = Base64.getDecoder().decode(ciphertext);
        if (payload.length <= NONCE_LENGTH + 16) {
            throw new GeneralSecurityException("Ciphertext AES-GCM invalido");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] nonce = new byte[NONCE_LENGTH];
        buffer.get(nonce);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, dek(masterKey, key), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad(key));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    static SecretKey dek(String masterKey, EncryptionKey key) throws GeneralSecurityException {
        byte[] ikm = masterKey.getBytes(StandardCharsets.UTF_8);
        byte[] prk = hmacSha256(HKDF_SALT, ikm);
        byte[] info = (
                "dek|" + key.getPurpose().name()
                        + "|v" + key.getVersion()
                        + "|" + HexFormat.of().formatHex(key.getPublicId())
                        + "|" + orgToken(key)
        ).getBytes(StandardCharsets.UTF_8);
        byte[] okm = hmacSha256(prk, concat(info, new byte[] {0x01}));
        return new SecretKeySpec(Arrays.copyOf(okm, 32), "AES");
    }

    static byte[] aad(EncryptionKey key) {
        return (orgToken(key) + "|" + key.getPurpose().name() + "|" + key.getVersion())
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String orgToken(EncryptionKey key) {
        return key.getOrganizationPublicId() == null
                ? "global"
                : HexFormat.of().formatHex(key.getOrganizationPublicId());
    }

    private static byte[] hmacSha256(byte[] hmacKey, byte[] message) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
        return mac.doFinal(message);
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] out = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, out, left.length, right.length);
        return out;
    }
}
