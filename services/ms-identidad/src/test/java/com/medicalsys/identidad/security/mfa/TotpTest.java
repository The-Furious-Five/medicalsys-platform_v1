package com.medicalsys.identidad.security.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** IAM-07: TOTP según RFC 6238, con sus vectores de prueba, y el Base32 de las URI otpauth. */
class TotpTest {

    /** Secreto de los vectores del Apéndice B de RFC 6238 (SHA-1). */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("coincide con los vectores de RFC 6238 (los 6 últimos dígitos de los de 8)")
    void matchesTheRfcVectors() {
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    @DisplayName("acepta un paso de desfase de reloj, no dos")
    void toleratesOneStepOfClockDrift() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = Totp.step(now);

        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, null)).isEmpty();
    }

    @Test
    @DisplayName("un código ya usado no vuelve a servir mientras sigue vigente")
    void rejectsAReusedCode() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = Totp.step(now);
        String code = Totp.code(RFC_SECRET, step);

        assertThat(Totp.verify(RFC_SECRET, code, now, step)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, code, now, step - 1)).hasValue(step);
    }

    @Test
    @DisplayName("rechaza lo que no son 6 dígitos")
    void rejectsMalformedCodes() {
        Instant now = Instant.now();
        assertThat(Totp.verify(RFC_SECRET, "12345", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "12a456", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, null, now, null)).isEmpty();
    }

    @Test
    @DisplayName("Base32 de RFC 4648 ida y vuelta, como lo leen las aplicaciones autenticadoras")
    void base32RoundTrip() {
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        byte[] secret = Totp.newSecret();
        assertThat(Base32.decode(Base32.encode(secret))).isEqualTo(secret);
        assertThat(Base32.decode("mzxw 6ytb oi")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> Base32.decode("MZ1W")).isInstanceOf(IllegalArgumentException.class);
    }
}
