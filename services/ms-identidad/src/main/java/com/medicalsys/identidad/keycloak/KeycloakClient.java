package com.medicalsys.identidad.keycloak;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.service.ApiException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Acceso al realm medicalsys con el cliente confidencial ms-identidad (IAM-03).
 *
 * <p>Las contraseñas se validan con Direct Access Grants y las cuentas se administran por la Admin
 * REST API con la service account del cliente (manage-users). Ninguna contraseña ni token se
 * escribe en los logs. Si Keycloak no responde, la operación falla con 503: no hay credenciales
 * locales de respaldo.
 */
@Component
public class KeycloakClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY = new ParameterizedTypeReference<>() { };

    /** Resultado de validar una contraseña en el token endpoint. */
    public enum PasswordCheck {
        VALID,
        /** Contraseña incorrecta, usuario inexistente o cuenta bloqueada: Keycloak no los distingue. */
        INVALID_CREDENTIALS,
        /** Contraseña correcta, pero temporal o vencida: Keycloak exige cambiarla (UPDATE_PASSWORD). */
        PASSWORD_CHANGE_REQUIRED,
        DISABLED
    }

    /** Datos de una cuenta en Keycloak. El username es el correo; se inicia sesión con él. */
    public record NewUser(String email, String firstName, String lastName, boolean enabled) { }

    private final IdentityProperties.Keycloak config;
    private final ObjectMapper json;
    private final RestClient http;

    private String serviceToken;
    private Instant serviceTokenRefreshAt = Instant.EPOCH;

    public KeycloakClient(IdentityProperties properties, ObjectMapper json) {
        this.config = properties.getKeycloak();
        this.json = json;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(config.getTimeout()).build());
        factory.setReadTimeout(config.getTimeout());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    // ---- credenciales -------------------------------------------------------------------------

    public PasswordCheck checkPassword(String username, String password) {
        MultiValueMap<String, String> form = clientForm("password");
        form.add("username", username);
        form.add("password", password);
        Map<String, Object> response = call(() -> http.post().uri(tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .exchange((request, res) -> {
                    Map<String, Object> body = res.bodyTo(JSON_OBJECT);
                    if (res.getStatusCode().is5xxServerError()) {
                        throw unavailable("token endpoint respondió " + res.getStatusCode().value(), null);
                    }
                    return Map.of("status", res.getStatusCode().value(), "body", body == null ? Map.of() : body);
                }));
        int status = (int) response.get("status");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.get("body");
        if (status == 200) {
            endSession((String) body.get("refresh_token"));
            return PasswordCheck.VALID;
        }
        String description = String.valueOf(body.get("error_description"));
        if (description.contains("not fully set up")) {
            return PasswordCheck.PASSWORD_CHANGE_REQUIRED;
        }
        if (description.contains("disabled")) {
            return PasswordCheck.DISABLED;
        }
        return PasswordCheck.INVALID_CREDENTIALS;
    }

    /** Fija la contraseña con la política del realm; si no la cumple, PasswordPolicyException. */
    public void setPassword(String userId, String password, boolean temporary) {
        Map<String, Object> credential = Map.of("type", "password", "value", password, "temporary", temporary);
        call(() -> http.put().uri(adminUrl("/users/" + userId + "/reset-password"))
                .headers(h -> h.setBearerAuth(serviceToken())).contentType(MediaType.APPLICATION_JSON)
                .body(credential)
                .exchange((request, res) -> {
                    if (res.getStatusCode().value() == 400) {
                        Map<String, Object> body = res.bodyTo(JSON_OBJECT);
                        throw new PasswordPolicyException(body == null ? "" : String.valueOf(body.get("error")));
                    }
                    requireSuccess(res.getStatusCode(), "reset-password");
                    return null;
                }));
    }

    // ---- cuentas -------------------------------------------------------------------------------

    public Optional<String> findUserIdByEmail(String email) {
        URI uri = UriComponentsBuilder.fromUriString(adminUrl("/users"))
                .queryParam("email", "{email}").queryParam("exact", true)
                .encode().buildAndExpand(email).toUri();
        List<Map<String, Object>> found = call(() -> http.get().uri(uri)
                .headers(h -> h.setBearerAuth(serviceToken())).retrieve().body(JSON_ARRAY));
        return found == null || found.isEmpty() ? Optional.empty() : Optional.of((String) found.getFirst().get("id"));
    }

    /** Falso si el usuario ya no existe en el realm (por ejemplo, tras recrear la base de Keycloak). */
    public boolean userExists(String userId) {
        return call(() -> http.get().uri(adminUrl("/users/" + userId)).headers(h -> h.setBearerAuth(serviceToken()))
                .exchange((request, res) -> {
                    if (res.getStatusCode().value() == 404) {
                        return false;
                    }
                    requireSuccess(res.getStatusCode(), "consulta de usuario");
                    return true;
                }));
    }

    /**
     * Crea la cuenta con el hash Argon2id que ya tenía ms-identidad: la contraseña no viaja ni se
     * conoce, y la política no se aplica a esa credencial heredada (sí a todo cambio posterior).
     */
    public String createUserWithLegacyHash(NewUser user, LegacyArgon2Hash hash) {
        Map<String, Object> credential = Map.of("type", "password",
                "credentialData", json.writeValueAsString(hash.credentialData()),
                "secretData", json.writeValueAsString(hash.secretData()));
        return createUser(user, List.of(credential));
    }

    private String createUser(NewUser user, List<Map<String, Object>> credentials) {
        Map<String, Object> body = Map.of("username", user.email(), "email", user.email(),
                "firstName", user.firstName(), "lastName", user.lastName(), "enabled", user.enabled(),
                "emailVerified", true, "credentials", credentials);
        URI location = call(() -> http.post().uri(adminUrl("/users")).headers(h -> h.setBearerAuth(serviceToken()))
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, res) -> {
                    if (res.getStatusCode().value() == 400) {
                        Map<String, Object> error = res.bodyTo(JSON_OBJECT);
                        throw new PasswordPolicyException(error == null ? "" : String.valueOf(error.get("error")));
                    }
                    requireSuccess(res.getStatusCode(), "alta de usuario");
                    return res.getHeaders().getLocation();
                }));
        String path = location == null ? "" : location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    // ---- bloqueo por fuerza bruta --------------------------------------------------------------

    public boolean isLockedOut(String userId) {
        Map<String, Object> state = call(() -> http.get().uri(adminUrl("/attack-detection/brute-force/users/" + userId))
                .headers(h -> h.setBearerAuth(serviceToken())).retrieve().body(JSON_OBJECT));
        return state != null && Boolean.TRUE.equals(state.get("disabled"));
    }

    /**
     * Levanta el bloqueo por fuerza bruta. Con bloqueo permanente Keycloak además deshabilita al
     * usuario, y borrar los intentos no lo vuelve a habilitar: hay que hacer las dos cosas.
     */
    public void unlock(String userId) {
        call(() -> http.delete().uri(adminUrl("/attack-detection/brute-force/users/" + userId))
                .headers(h -> h.setBearerAuth(serviceToken())).retrieve().toBodilessEntity());
        call(() -> http.put().uri(adminUrl("/users/" + userId)).headers(h -> h.setBearerAuth(serviceToken()))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("enabled", true))
                .retrieve().toBodilessEntity());
    }

    // ---- infraestructura -----------------------------------------------------------------------

    private synchronized String serviceToken() {
        if (serviceToken != null && Instant.now().isBefore(serviceTokenRefreshAt)) {
            return serviceToken;
        }
        Map<String, Object> body = call(() -> http.post().uri(tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(clientForm("client_credentials"))
                .retrieve().body(JSON_OBJECT));
        if (body == null || body.get("access_token") == null) {
            throw unavailable("sin token de la service account", null);
        }
        long expiresIn = ((Number) body.getOrDefault("expires_in", 60)).longValue();
        serviceToken = (String) body.get("access_token");
        serviceTokenRefreshAt = Instant.now().plusSeconds(Math.max(5, expiresIn - 30));
        return serviceToken;
    }

    /** Cierra la sesión que abre el password grant: ms-identidad emite su propio JWT. */
    private void endSession(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        MultiValueMap<String, String> form = clientForm(null);
        form.add("refresh_token", refreshToken);
        try {
            http.post().uri(baseRealmUrl() + "/protocol/openid-connect/logout")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            log.debug("No se pudo cerrar la sesion de Keycloak; expira sola", ex);
        }
    }

    private MultiValueMap<String, String> clientForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (grantType != null) {
            form.add("grant_type", grantType);
        }
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        return form;
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (ApiException | PasswordPolicyException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw unavailable(ex.getClass().getSimpleName(), ex);
        }
    }

    private void requireSuccess(HttpStatusCode status, String operation) {
        if (!status.is2xxSuccessful()) {
            throw unavailable(operation + " respondió " + status.value(), null);
        }
    }

    private ApiException unavailable(String reason, Exception cause) {
        log.error("Keycloak no disponible: {}", reason, cause);
        return ApiException.identityProviderUnavailable();
    }

    private String baseRealmUrl() {
        return config.getBaseUrl() + "/realms/" + config.getRealm();
    }

    private String tokenUrl() {
        return baseRealmUrl() + "/protocol/openid-connect/token";
    }

    private String adminUrl(String path) {
        return config.getBaseUrl() + "/admin/realms/" + config.getRealm() + path;
    }
}
