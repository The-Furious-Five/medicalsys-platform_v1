package com.medicalsys.historial.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.historial.domain.model.ClinicalEncounter;
import com.medicalsys.historial.support.TestFixtures;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El sello del encuentro debe ser estable y romperse ante cualquier alteracion. */
class ContentHasherTest {

    private final ContentHasher hasher = new ContentHasher();

    @Test
    @DisplayName("produce un SHA-256 hexadecimal de 64 caracteres")
    void producesSha256Hex() {
        ClinicalEncounter encounter = TestFixtures.draftEncounter(TestFixtures.medicalRecord());

        assertThat(hasher.hashEncounter(encounter)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("el mismo contenido produce siempre el mismo sello")
    void isDeterministic() {
        ClinicalEncounter encounter = TestFixtures.draftEncounter(TestFixtures.medicalRecord());

        assertThat(hasher.hashEncounter(encounter)).isEqualTo(hasher.hashEncounter(encounter));
    }

    @Test
    @DisplayName("alterar la narrativa cifrada rompe el sello")
    void detectsTampering() {
        ClinicalEncounter encounter = TestFixtures.draftEncounter(TestFixtures.medicalRecord());
        String before = hasher.hashEncounter(encounter);

        encounter.replaceNarrative(null, "otro-ciphertext".getBytes(StandardCharsets.UTF_8), null, null, null,
                TestFixtures.KEY_ID);

        assertThat(hasher.hashEncounter(encounter)).isNotEqualTo(before);
    }
}
