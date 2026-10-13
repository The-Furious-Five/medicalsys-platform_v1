package com.medicalsys.identidad.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Contraseña temporal para el alta y el reinicio (IAM-04/IAM-05). Sirve para un solo ingreso:
 * Keycloak exige cambiarla (UPDATE_PASSWORD). El administrador nunca define la contraseña definitiva.
 *
 * <p>Cuatro bloques como {@code Kq7m-Xw3p-Rt9v-Hn4c}: 19 caracteres. Cada bloque lleva una mayúscula,
 * una minúscula y un dígito, y los guiones son el carácter especial, así que siempre cumple la política
 * de IAM-02. Se omiten caracteres que se confunden al dictarla (O/0, l/1/I). Unos 67 bits de entropía.
 */
@Component
public class TemporaryPasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder password = new StringBuilder(19);
        for (int block = 0; block < 4; block++) {
            if (block > 0) {
                password.append('-');
            }
            password.append(pick(UPPER)).append(pick(LOWER)).append(pick(DIGITS)).append(pick(LOWER));
        }
        return password.toString();
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
