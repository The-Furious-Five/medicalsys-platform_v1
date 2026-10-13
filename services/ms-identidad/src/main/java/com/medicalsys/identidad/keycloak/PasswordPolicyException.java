package com.medicalsys.identidad.keycloak;

import java.util.Map;

/**
 * Keycloak rechazó una contraseña por la política del realm (IAM-02). Lleva la clave de Keycloak
 * y su explicación en español, que es lo que ve el usuario.
 */
public class PasswordPolicyException extends RuntimeException {

    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("invalidPasswordMinLengthMessage", "La contraseña debe tener al menos 12 caracteres."),
            Map.entry("invalidPasswordMaxLengthMessage", "La contraseña no puede superar los 64 caracteres."),
            Map.entry("invalidPasswordMinUpperCaseCharsMessage", "La contraseña debe incluir al menos una letra mayúscula."),
            Map.entry("invalidPasswordMinLowerCaseCharsMessage", "La contraseña debe incluir al menos una letra minúscula."),
            Map.entry("invalidPasswordMinDigitsMessage", "La contraseña debe incluir al menos un número."),
            Map.entry("invalidPasswordMinSpecialCharsMessage", "La contraseña debe incluir al menos un carácter especial (por ejemplo, un espacio o !)."),
            Map.entry("invalidPasswordNotUsernameMessage", "La contraseña no puede ser igual a tu nombre de usuario."),
            Map.entry("invalidPasswordNotEmailMessage", "La contraseña no puede ser igual a tu correo."),
            Map.entry("invalidPasswordBlacklistedMessage", "Esa contraseña es demasiado común. Elige una frase menos predecible."),
            Map.entry("invalidPasswordHistoryMessage", "No puedes reutilizar ninguna de tus últimas 5 contraseñas."));

    private final String policyKey;

    public PasswordPolicyException(String policyKey) {
        super(MESSAGES.getOrDefault(policyKey, "La contraseña no cumple la política de contraseñas."));
        this.policyKey = policyKey;
    }

    /** Clave de Keycloak, por ejemplo {@code invalidPasswordHistoryMessage}. */
    public String getPolicyKey() {
        return policyKey;
    }
}
