package com.medicalsys.identidad.security.mfa;

import com.medicalsys.identidad.config.IdentityProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Cifra el secreto TOTP antes de guardarlo (AES-256-GCM): con una copia de la base sola no se pueden
 * generar códigos. Formato: versión (1 byte) | IV de 12 bytes | texto cifrado con la etiqueta de 128 bits.
 * El identificador de la cuenta va como dato asociado, así que un secreto copiado a otra fila no descifra.
 *
 * <p>La llave se deriva con HMAC-SHA256 de IDENTITY_MFA_KEY, o de JWT_SECRET si no está definida, con una
 * etiqueta propia: nunca es la misma que firma los tokens.
 */
@Component
public class MfaSecretCipher {

    private static final Logger log = LoggerFactory.getLogger(MfaSecretCipher.class);
    private static final byte VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public MfaSecretCipher(IdentityProperties properties) {
        String material = properties.getMfa().getEncryptionKey();
        if (!StringUtils.hasText(material)) {
            log.warn("IDENTITY_MFA_KEY no esta definida: la llave de los secretos TOTP se deriva de JWT_SECRET");
            material = properties.getJwt().getSecret();
        }
        if (!StringUtils.hasText(material) || material.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("IDENTITY_MFA_KEY (o JWT_SECRET) debe tener al menos 32 bytes");
        }
        this.key = new SecretKeySpec(derive(material), "AES");
    }

    public byte[] encrypt(byte[] secret, String accountId) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(accountId.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(secret);
            return ByteBuffer.allocate(1 + IV_BYTES + encrypted.length).put(VERSION).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el secreto TOTP", ex);
        }
    }

    public byte[] decrypt(byte[] stored, String accountId) {
        if (stored == null || stored.length <= 1 + IV_BYTES || stored[0] != VERSION) {
            throw new IllegalStateException("Secreto TOTP con formato desconocido");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, IV_BYTES));
            cipher.updateAAD(accountId.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(stored, 1 + IV_BYTES, stored.length - 1 - IV_BYTES);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo descifrar el secreto TOTP (¿cambió la llave?)", ex);
        }
    }

    private static byte[] derive(String material) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(material.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal("medicalsys-identidad/totp-secret/v1".getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 no disponible", ex);
        }
    }
}
