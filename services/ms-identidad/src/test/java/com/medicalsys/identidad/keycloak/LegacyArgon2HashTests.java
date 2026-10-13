package com.medicalsys.identidad.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class LegacyArgon2HashTests {

    @Test
    @DisplayName("traduce el hash de Spring Security a la credencial de Keycloak conservando sus parámetros")
    void translatesSpringHash() {
        String encoded = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("Demo1234!");

        LegacyArgon2Hash hash = LegacyArgon2Hash.parse(encoded);

        assertThat(hash.type()).isEqualTo("id");
        assertThat(hash.version()).isEqualTo("1.3");
        assertThat(hash.memoryKib()).isEqualTo(16384);
        assertThat(hash.iterations()).isEqualTo(2);
        assertThat(hash.parallelism()).isEqualTo(1);
        assertThat(hash.salt()).hasSize(16);
        assertThat(hash.hash()).hasSize(32);

        Map<String, Object> credentialData = hash.credentialData();
        assertThat(credentialData).containsEntry("algorithm", "argon2").containsEntry("hashIterations", 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) credentialData.get("additionalParameters");
        assertThat(parameters)
                .containsEntry("type", List.of("id"))
                .containsEntry("version", List.of("1.3"))
                .containsEntry("memory", List.of("16384"))
                .containsEntry("hashLength", List.of("32"))
                .containsEntry("parallelism", List.of("1"));
    }

    @Test
    @DisplayName("secretData lleva hash y sal en Base64 con relleno, decodificables a los bytes originales")
    void secretDataIsPaddedBase64() {
        LegacyArgon2Hash hash = LegacyArgon2Hash.parse(
                "$argon2id$v=19$m=16384,t=2,p=1$R6Ai/BIAJCC4aNw+WsQa4Q$vgn8Tq81Gd6RR6fEEqEUdAiNAkOpublGdwxtnaAltF0");

        Map<String, Object> secret = hash.secretData();

        assertThat((String) secret.get("salt")).isEqualTo("R6Ai/BIAJCC4aNw+WsQa4Q==");
        assertThat((String) secret.get("value")).endsWith("=");
        assertThat(Base64.getDecoder().decode((String) secret.get("value"))).isEqualTo(hash.hash());
    }

    @Test
    @DisplayName("rechaza lo que no es un hash Argon2 de Spring Security")
    void rejectsOtherFormats() {
        assertThatThrownBy(() -> LegacyArgon2Hash.parse("$2a$10$abcdefghijklmnopqrstuv"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LegacyArgon2Hash.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
