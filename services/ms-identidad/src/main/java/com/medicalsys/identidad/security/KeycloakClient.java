package com.medicalsys.identidad.security;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.service.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente del realm de Keycloak que guarda las contrasenas.
 *
 * <p>Usa el cliente confidencial {@code ms-identidad} de dos formas: Direct Access Grants para
 * verificar una contrasena contra el token endpoint, y su service account (rol manage-users de
 * realm-management) para crear cuentas y fijar contrasenas por la Admin REST API. El token que
 * devuelve Keycloak se descarta: el JWT de MedicalSys lo sigue emitiendo {@link TokenService}.
 */
@Component
public class KeycloakClient {

    public enum PasswordCheck { VALID, INVALID, LOCKED, PASSWORD_CHANGE_REQUIRED }

    private static final Duration TOKEN_MARGIN = Duration.ofSeconds(30);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new ParameterizedTypeReference<>() { };

    private final IdentityProperties.Keycloak config;
    private final RestClient http;

    private String adminToken;
    private Instant adminTokenExpiresAt = Instant.EPOCH;

    @Autowired
    public KeycloakClient(IdentityProperties properties) {
        this(properties, RestClient.builder());
    }

    /** Las pruebas pasan un builder enlazado a MockRestServiceServer. */
    KeycloakClient(IdentityProperties properties, RestClient.Builder builder) {
        this.config = properties.getKeycloak();
        this.http = builder.baseUrl(config.getBaseUrl()).build();
    }

    /** Verifica la contrasena con el grant password del token endpoint. */
    public PasswordCheck checkPassword(String username, String password) {
        MultiValueMap<String, String> form = clientForm("password");
        form.add("username", username);
        form.add("password", password);
        try {
            http.post().uri(tokenPath())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return PasswordCheck.VALID;
        } catch (HttpClientErrorException ex) {
            String body = ex.getResponseBodyAsString();
            // Acciones requeridas pendientes (UPDATE_PASSWORD por contrasena vencida o temporal).
            if (body.contains("Account is not fully set up")) {
                return PasswordCheck.PASSWORD_CHANGE_REQUIRED;
            }
            // Cuenta deshabilitada, p. ej. por bloqueo permanente de la deteccion de fuerza bruta.
            if (body.contains("Account disabled") || body.contains("temporarily disabled")) {
                return PasswordCheck.LOCKED;
            }
            if (body.contains("invalid_grant")) {
                // Keycloak responde "Invalid user credentials" tambien durante un bloqueo temporal
                // para no filtrar informacion; la deteccion de fuerza bruta lo distingue.
                return isTemporarilyLocked(username) ? PasswordCheck.LOCKED : PasswordCheck.INVALID;
            }
            throw unexpected(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    /** Crea la cuenta si no existe. No toca la contrasena de una cuenta existente. */
    public boolean ensureUser(String email, String fullName, String password) {
        if (findUserId(email) != null) {
            return false;
        }
        createUser(email, fullName, password);
        return true;
    }

    /** Fija la contrasena (no temporal), creando la cuenta en Keycloak si todavia no existe. */
    public void setPassword(String email, String fullName, String password) {
        String id = findUserId(email);
        if (id == null) {
            createUser(email, fullName, password);
            return;
        }
        adminCall(() -> http.put().uri(adminPath("/users/{id}/reset-password"), id)
                .headers(headers -> headers.setBearerAuth(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(credential(password))
                .retrieve()
                .toBodilessEntity());
    }

    private void createUser(String email, String fullName, String password) {
        String[] names = splitName(fullName);
        Map<String, Object> user = Map.of(
                "username", email,
                "email", email,
                "firstName", names[0],
                "lastName", names[1],
                "enabled", true,
                // Sin estos datos Keycloak exige completar el perfil y el direct grant falla.
                "emailVerified", true,
                "credentials", List.of(credential(password)));
        adminCall(() -> http.post().uri(adminPath("/users"))
                .headers(headers -> headers.setBearerAuth(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity());
    }

    String findUserId(String email) {
        List<Map<String, Object>> found = adminCall(() -> http.get()
                .uri(uri -> uri.path(adminPath("/users"))
                        .queryParam("username", email)
                        .queryParam("exact", true)
                        .build())
                .headers(headers -> headers.setBearerAuth(adminToken()))
                .retrieve()
                .body(JSON_ARRAY));
        return found == null || found.isEmpty() ? null : String.valueOf(found.get(0).get("id"));
    }

    private boolean isTemporarilyLocked(String username) {
        try {
            String id = findUserId(username);
            if (id == null) {
                return false;
            }
            Map<String, Object> status = http.get()
                    .uri(adminPath("/attack-detection/brute-force/users/{id}"), id)
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .retrieve()
                    .body(JSON_OBJECT);
            return status != null && Boolean.TRUE.equals(status.get("disabled"));
        } catch (RuntimeException ex) {
            // Consulta auxiliar: si falla, se responde como credencial invalida.
            return false;
        }
    }

    private synchronized String adminToken() {
        if (adminToken != null && Instant.now().isBefore(adminTokenExpiresAt)) {
            return adminToken;
        }
        try {
            Map<String, Object> response = http.post().uri(tokenPath())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(clientForm("client_credentials"))
                    .retrieve()
                    .body(JSON_OBJECT);
            if (response == null || response.get("access_token") == null) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Keycloak no disponible",
                        "Keycloak no devolvio un token para el service account");
            }
            long expiresIn = ((Number) response.getOrDefault("expires_in", 60)).longValue();
            adminToken = (String) response.get("access_token");
            adminTokenExpiresAt = Instant.now().plusSeconds(expiresIn).minus(TOKEN_MARGIN);
            return adminToken;
        } catch (RestClientResponseException ex) {
            throw unexpected(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    private <T> T adminCall(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.BadRequest ex) {
            // La politica de contrasenas del realm rechaza la contrasena.
            throw ApiException.badRequest("La contrasena no cumple la politica: " + describe(ex));
        } catch (HttpClientErrorException.Conflict ex) {
            throw ApiException.conflict("La cuenta ya existe en Keycloak");
        } catch (RestClientResponseException ex) {
            throw unexpected(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    private MultiValueMap<String, String> clientForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        return form;
    }

    private static Map<String, Object> credential(String password) {
        return Map.of("type", "password", "value", password, "temporary", false);
    }

    private static String[] splitName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[] {trimmed, trimmed};
        }
        return new String[] {trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }

    private String tokenPath() {
        return "/realms/" + config.getRealm() + "/protocol/openid-connect/token";
    }

    private String adminPath(String suffix) {
        return "/admin/realms/" + config.getRealm() + suffix;
    }

    private static String describe(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body.isBlank() ? ex.getStatusText() : body;
    }

    private static ApiException unexpected(RestClientResponseException ex) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "Error de Keycloak",
                "Keycloak respondio " + ex.getStatusCode().value() + ": " + describe(ex));
    }

    private static ApiException unavailable(RuntimeException cause) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Keycloak no disponible",
                "No fue posible contactar Keycloak: " + cause.getMessage());
    }
}
