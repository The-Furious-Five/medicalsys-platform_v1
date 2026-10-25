package com.medicalsys.identidad.security.mfa;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Códigos de un solo uso por tiempo (TOTP, RFC 6238 sobre HOTP, RFC 4226) con los parámetros que
 * entiende Google Authenticator: HMAC-SHA1, 6 dígitos y pasos de 30 segundos. Se acepta un paso de
 * desfase hacia cada lado por la diferencia de reloj del teléfono (RFC 6238 §5.2).
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    /** 160 bits: el largo que recomienda RFC 4226 §4 para el secreto compartido. */
    public static final int SECRET_BYTES = 20;
    private static final int WINDOW = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    public static byte[] newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    public static long step(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), PERIOD_SECONDS);
    }

    /**
     * Paso al que corresponde {@code code} dentro de la ventana, si es mayor que {@code lastUsedStep}:
     * un código ya aceptado no vuelve a servir mientras siga vigente. La comparación es de tiempo
     * constante para no filtrar dígitos por la latencia.
     */
    public static OptionalLong verify(byte[] secret, String code, Instant now, Long lastUsedStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = step(now);
        byte[] expected = code.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        OptionalLong accepted = OptionalLong.empty();
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            byte[] candidate = code(secret, step).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, candidate) && (lastUsedStep == null || step > lastUsedStep)) {
                accepted = OptionalLong.of(step);
            }
        }
        return accepted;
    }

    /** HOTP (RFC 4226 §5.3): truncamiento dinámico del HMAC del contador. */
    public static String code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA1 no disponible", ex);
        }
    }
}
