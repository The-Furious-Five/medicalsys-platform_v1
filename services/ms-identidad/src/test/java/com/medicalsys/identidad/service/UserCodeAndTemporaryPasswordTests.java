package com.medicalsys.identidad.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** IAM-04: formato del User ID y contraseñas temporales que siempre cumplen la política de IAM-02. */
class UserCodeAndTemporaryPasswordTests {

    @Test
    @DisplayName("el User ID es MS-AA-NNNNN: año de alta en dos dígitos y correlativo de cinco")
    void userCodeFormat() {
        assertThat(UserCodeGenerator.format(2026, 1)).isEqualTo("MS-26-00001");
        assertThat(UserCodeGenerator.format(2026, 42)).isEqualTo("MS-26-00042");
        assertThat(UserCodeGenerator.format(2031, 99999)).isEqualTo("MS-31-99999");
        // Pasado el correlativo de cinco dígitos crece, sin truncar ni reutilizar códigos.
        assertThat(UserCodeGenerator.format(2026, 100000)).isEqualTo("MS-26-100000");
        assertThat(UserCodeGenerator.format(2026, 7)).matches("^MS-[0-9]{2}-[0-9]{5,}$");
    }

    @Test
    @DisplayName("la contraseña temporal cumple cada regla de la política, en 2000 generaciones")
    void temporaryPasswordAlwaysMeetsPolicy() {
        TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String password = generator.generate();
            assertThat(password).hasSizeBetween(12, 64);
            assertThat(password).matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*[0-9].*")
                    .matches(".*[^A-Za-z0-9].*");
            assertThat(password).doesNotContainPattern("[O0l1I]");
            seen.add(password);
        }
        assertThat(seen).as("sin repeticiones").hasSize(2000);
    }
}
