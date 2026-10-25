package com.medicalsys.identidad.security.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.identidad.config.IdentityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** IAM-07: el secreto TOTP solo se guarda cifrado y atado a su cuenta. */
class MfaSecretCipherTest {

    private static final String ACCOUNT = "00000000-0000-4000-8000-000000000901";

    @Test
    @DisplayName("cifra y descifra; dos cifrados del mismo secreto no se parecen")
    void roundTripWithRandomIv() {
        MfaSecretCipher cipher = new MfaSecretCipher(properties("una-llave-de-pruebas-de-mas-de-32-bytes!"));
        byte[] secret = Totp.newSecret();

        byte[] first = cipher.encrypt(secret, ACCOUNT);

        assertThat(cipher.decrypt(first, ACCOUNT)).isEqualTo(secret);
        assertThat(cipher.encrypt(secret, ACCOUNT)).isNotEqualTo(first);
        assertThat(new String(first, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain(Base32.encode(secret));
    }

    @Test
    @DisplayName("copiado a otra cuenta, o con otra llave, no descifra")
    void isBoundToTheAccountAndTheKey() {
        MfaSecretCipher cipher = new MfaSecretCipher(properties("una-llave-de-pruebas-de-mas-de-32-bytes!"));
        byte[] stored = cipher.encrypt(Totp.newSecret(), ACCOUNT);

        assertThatThrownBy(() -> cipher.decrypt(stored, "00000000-0000-4000-8000-000000000902"))
                .isInstanceOf(IllegalStateException.class);
        MfaSecretCipher other = new MfaSecretCipher(properties("otra-llave-de-pruebas-de-mas-de-32-bytes"));
        assertThatThrownBy(() -> other.decrypt(stored, ACCOUNT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sin IDENTITY_MFA_KEY usa JWT_SECRET; con menos de 32 bytes no arranca")
    void requiresEnoughKeyMaterial() {
        IdentityProperties fallback = new IdentityProperties();
        fallback.getJwt().setSecret("medicalsys-local-jwt-secret-at-least-32-bytes");
        new MfaSecretCipher(fallback);

        assertThatThrownBy(() -> new MfaSecretCipher(properties("corta"))).isInstanceOf(IllegalStateException.class);
    }

    private static IdentityProperties properties(String key) {
        IdentityProperties properties = new IdentityProperties();
        properties.getMfa().setEncryptionKey(key);
        return properties;
    }
}
