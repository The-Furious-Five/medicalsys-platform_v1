package com.medicalsys.historial.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.historial.config.HistorialProperties;
import com.medicalsys.historial.config.HistorialProperties.CatalogValidationMode;
import com.medicalsys.historial.config.HistorialProperties.CryptoMode;
import com.medicalsys.historial.support.TestFixtures;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El cifrado local debe producir ciphertext utilizable y nunca dejar el texto en claro visible. */
class LocalNarrativeCipherTest {

    private static final String NARRATIVE = "Paciente ficticio refiere cefalea de 3 dias de evolucion.";

    private final LocalNarrativeCipher cipher = new LocalNarrativeCipher(properties());

    @Test
    @DisplayName("cifra y descifra la narrativa conservando el texto original")
    void encryptsAndDecrypts() {
        byte[] ciphertext = cipher.open(TestFixtures.ORGANIZATION_ID).encrypt(NARRATIVE);

        assertThat(ciphertext).isNotNull();
        assertThat(cipher.decrypt(TestFixtures.KEY_ID, ciphertext)).isEqualTo(NARRATIVE);
    }

    @Test
    @DisplayName("el ciphertext no contiene el texto en claro")
    void ciphertextDoesNotLeakPlaintext() {
        byte[] ciphertext = cipher.open(TestFixtures.ORGANIZATION_ID).encrypt(NARRATIVE);

        assertThat(new String(ciphertext, StandardCharsets.ISO_8859_1)).doesNotContain("cefalea");
    }

    @Test
    @DisplayName("dos cifrados del mismo texto producen valores distintos por el nonce aleatorio")
    void usesRandomNonce() {
        var session = cipher.open(TestFixtures.ORGANIZATION_ID);

        assertThat(session.encrypt(NARRATIVE)).isNotEqualTo(session.encrypt(NARRATIVE));
    }

    @Test
    @DisplayName("un texto ausente o vacio no genera ciphertext")
    void skipsEmptyValues() {
        var session = cipher.open(TestFixtures.ORGANIZATION_ID);

        assertThat(session.encrypt(null)).isNull();
        assertThat(session.encrypt("   ")).isNull();
        assertThat(cipher.decrypt(TestFixtures.KEY_ID, null)).isNull();
    }

    @Test
    @DisplayName("expone la clave declarada para persistirla junto al dato")
    void exposesConfiguredKey() {
        assertThat(cipher.open(TestFixtures.ORGANIZATION_ID).keyPublicId()).isEqualTo(TestFixtures.KEY_ID);
    }

    @Test
    @DisplayName("un valor corrupto falla en vez de devolver basura")
    void rejectsCorruptedCiphertext() {
        assertThatThrownBy(() -> cipher.decrypt(TestFixtures.KEY_ID, new byte[] {9, 9, 9}))
                .isInstanceOf(IllegalStateException.class);
    }

    private static HistorialProperties properties() {
        return new HistorialProperties(
                new HistorialProperties.Crypto(CryptoMode.LOCAL, "http://ms-criptografia:8084",
                        "clave-local-de-prueba", TestFixtures.KEY_ID, "", Duration.ofSeconds(2),
                        Duration.ofSeconds(3)),
                new HistorialProperties.Catalogs(CatalogValidationMode.LOCAL, "http://ms-catalogos:8083",
                        Duration.ofSeconds(2), Duration.ofSeconds(3),
                        new HistorialProperties.Catalogs.Local(Set.of("J00"), Set.of("X_RAY"),
                                Set.of("PARACETAMOL_500"))),
                new HistorialProperties.Patients("http://ms-pacientes:8085", Duration.ofSeconds(2),
                        Duration.ofSeconds(3)),
                new HistorialProperties.Numbering("EXP", "REC", 5));
    }
}
