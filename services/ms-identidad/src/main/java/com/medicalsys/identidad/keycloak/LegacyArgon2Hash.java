package com.medicalsys.identidad.keycloak;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hash Argon2 de ms-identidad (formato PHC de Spring Security) traducido a la credencial de
 * Keycloak, para migrar cuentas sin conocer ni volver a pedir la contraseña.
 *
 * <p>Spring guarda {@code $argon2id$v=19$m=16384,t=2,p=1$<sal>$<hash>} en Base64 sin relleno.
 * Keycloak verifica una credencial importada con los parámetros que trae en {@code credentialData},
 * así que conserva memoria, iteraciones y paralelismo originales.
 */
public record LegacyArgon2Hash(String type, String version, int memoryKib, int iterations, int parallelism,
                               byte[] salt, byte[] hash) {

    private static final Pattern PHC = Pattern.compile(
            "^\\$argon2(id|i|d)\\$v=(\\d+)\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$([A-Za-z0-9+/]+)\\$([A-Za-z0-9+/]+)$");

    public static LegacyArgon2Hash parse(String encoded) {
        Matcher m = PHC.matcher(encoded == null ? "" : encoded);
        if (!m.matches()) {
            throw new IllegalArgumentException("El hash no tiene el formato Argon2 de Spring Security");
        }
        String version = switch (m.group(2)) {
            case "19" -> "1.3";
            case "16" -> "1.0";
            default -> throw new IllegalArgumentException("Versión de Argon2 desconocida: " + m.group(2));
        };
        Base64.Decoder decoder = Base64.getDecoder();
        return new LegacyArgon2Hash(m.group(1), version, Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)),
                decoder.decode(m.group(6)), decoder.decode(m.group(7)));
    }

    /** {@code credentialData} de un CredentialRepresentation de tipo password. */
    public Map<String, Object> credentialData() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("hashLength", List.of(Integer.toString(hash.length)));
        parameters.put("memory", List.of(Integer.toString(memoryKib)));
        parameters.put("type", List.of(type));
        parameters.put("version", List.of(version));
        parameters.put("parallelism", List.of(Integer.toString(parallelism)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hashIterations", iterations);
        data.put("algorithm", "argon2");
        data.put("additionalParameters", parameters);
        return data;
    }

    /** {@code secretData}: hash y sal en Base64 con relleno, como los serializa Keycloak. */
    public Map<String, Object> secretData() {
        Base64.Encoder encoder = Base64.getEncoder();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("value", encoder.encodeToString(hash));
        data.put("salt", encoder.encodeToString(salt));
        return data;
    }
}
