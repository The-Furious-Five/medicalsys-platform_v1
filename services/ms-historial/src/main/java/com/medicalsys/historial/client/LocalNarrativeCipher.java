package com.medicalsys.historial.client;

import com.medicalsys.historial.config.HistorialProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Cifrado AES-256-GCM en proceso para el entorno local del MVP.
 *
 * <p>Existe para que la rebanada vertical funcione de extremo a extremo antes de que
 * ms-criptografia publique su API: el ciphertext persistido y el contrato de la columna son los
 * mismos. Al activar {@code CRYPTO_MODE=REMOTE} la operacion se delega sin tocar el modelo.
 *
 * <p>No sustituye a un KMS: la clave de demostracion vive en una variable de entorno local, no
 * rota y nunca se registra en logs.
 */
@Component
@ConditionalOnProperty(prefix = "medicalsys.crypto", name = "mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalNarrativeCipher implements NarrativeCipher {

    private static final Logger log = LoggerFactory.getLogger(LocalNarrativeCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final byte FORMAT_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;
    private final UUID keyPublicId;

    public LocalNarrativeCipher(HistorialProperties properties) {
        HistorialProperties.Crypto crypto = properties.crypto();
        this.keyPublicId = crypto.keyPublicId();
        this.key = deriveKey(crypto.demoMasterKey());
        log.warn("ms-historial cifra la narrativa en proceso (CRYPTO_MODE=LOCAL). "
                + "Para delegar en ms-criptografia use CRYPTO_MODE=REMOTE.");
    }

    @Override
    public CipherSession open(UUID organizationId) {
        return new LocalSession();
    }

    @Override
    public String decrypt(UUID keyPublicId, byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length == 0) {
            return null;
        }
        if (ciphertext.length <= 1 + IV_LENGTH || ciphertext[0] != FORMAT_VERSION) {
            throw new IllegalStateException("El valor cifrado no tiene el formato local esperado");
        }
        try {
            byte[] iv = Arrays.copyOfRange(ciphertext, 1, 1 + IV_LENGTH);
            byte[] payload = Arrays.copyOfRange(ciphertext, 1 + IV_LENGTH, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible descifrar la narrativa clinica", e);
        }
    }

    /**
     * Deriva una clave AES-256 estable a partir del secreto local.
     *
     * <p>Si la variable llega vacia se genera una clave efimera: el servicio arranca y la demo
     * funciona, pero los datos cifrados con el arranque anterior dejan de leerse. Es intencional:
     * evita que un secreto por defecto se confunda con uno configurado.
     */
    private SecretKeySpec deriveKey(String masterKey) {
        try {
            byte[] material;
            if (masterKey == null || masterKey.isBlank()) {
                log.warn("DEMO_MASTER_KEY no esta definida: se usara una clave efimera de este arranque.");
                material = new byte[32];
                random.nextBytes(material);
            } else {
                material = MessageDigest.getInstance("SHA-256")
                        .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            }
            return new SecretKeySpec(material, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible derivar la clave local de demostracion", e);
        }
    }

    private final class LocalSession implements CipherSession {

        @Override
        public UUID keyPublicId() {
            return keyPublicId;
        }

        @Override
        public byte[] encrypt(String plaintext) {
            if (plaintext == null || plaintext.isBlank()) {
                return null;
            }
            try {
                byte[] iv = new byte[IV_LENGTH];
                random.nextBytes(iv);
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
                byte[] payload = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

                byte[] result = new byte[1 + IV_LENGTH + payload.length];
                result[0] = FORMAT_VERSION;
                System.arraycopy(iv, 0, result, 1, IV_LENGTH);
                System.arraycopy(payload, 0, result, 1 + IV_LENGTH, payload.length);
                return result;
            } catch (Exception e) {
                throw new IllegalStateException("No fue posible cifrar la narrativa clinica", e);
            }
        }
    }
}
