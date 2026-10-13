package com.medicalsys.identidad.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.medicalsys.identidad.config.IdentityProperties;
import com.medicalsys.identidad.security.KeycloakClient.PasswordCheck;
import com.medicalsys.identidad.service.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Mapeo de las respuestas reales del token endpoint y de la Admin API de Keycloak. */
class KeycloakClientTests {

    private static final String BASE = "http://keycloak:8080";
    private static final String TOKEN = BASE + "/realms/medicalsys/protocol/openid-connect/token";
    private static final String USERS = BASE + "/admin/realms/medicalsys/users";
    private static final String EMAIL = "doctor@medicalsys.demo";

    private MockRestServiceServer server;
    private KeycloakClient client;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = new IdentityProperties();
        properties.getKeycloak().setBaseUrl(BASE);
        properties.getKeycloak().setClientSecret("secreto");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakClient(properties, builder);
    }

    @Test
    @DisplayName("token emitido por el grant password: contrasena valida")
    void validPassword() {
        server.expect(requestTo(TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "password", "client_id", "ms-identidad", "username", EMAIL)))
                .andRespond(withSuccess("{\"access_token\":\"kc\",\"token_type\":\"Bearer\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.checkPassword(EMAIL, "Demo1234!")).isEqualTo(PasswordCheck.VALID);
        server.verify();
    }

    @Test
    @DisplayName("invalid_grant sin bloqueo de fuerza bruta: contrasena invalida")
    void invalidPassword() {
        expectInvalidGrant();
        expectBruteForceStatus(false);

        assertThat(client.checkPassword(EMAIL, "mala")).isEqualTo(PasswordCheck.INVALID);
        server.verify();
    }

    @Test
    @DisplayName("invalid_grant con la cuenta bloqueada por fuerza bruta: bloqueada")
    void temporarilyLocked() {
        expectInvalidGrant();
        expectBruteForceStatus(true);

        assertThat(client.checkPassword(EMAIL, "mala")).isEqualTo(PasswordCheck.LOCKED);
        server.verify();
    }

    @Test
    @DisplayName("cuenta deshabilitada en Keycloak: bloqueada")
    void disabledAccount() {
        server.expect(requestTo(TOKEN)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_grant\",\"error_description\":\"Account disabled\"}"));

        assertThat(client.checkPassword(EMAIL, "Demo1234!")).isEqualTo(PasswordCheck.LOCKED);
    }

    @Test
    @DisplayName("acciones requeridas pendientes (contrasena vencida): cambio obligatorio")
    void passwordChangeRequired() {
        server.expect(requestTo(TOKEN)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_grant\",\"error_description\":\"Account is not fully set up\"}"));

        assertThat(client.checkPassword(EMAIL, "Demo1234!")).isEqualTo(PasswordCheck.PASSWORD_CHANGE_REQUIRED);
    }

    @Test
    @DisplayName("fijar contrasena de una cuenta que no existe en Keycloak la crea con el service account")
    void setPasswordCreatesMissingUser() {
        expectServiceAccountToken();
        server.expect(requestTo(startsWith(USERS + "?username=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USERS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"emailVerified\":true")))
                .andRespond(withStatus(HttpStatus.CREATED));

        client.setPassword(EMAIL, "Ricardo Salazar Peredo", "Demo1234!");
        server.verify();
    }

    @Test
    @DisplayName("la politica de contrasenas del realm rechaza la nueva contrasena: 400")
    void passwordPolicyRejection() {
        expectServiceAccountToken();
        server.expect(requestTo(startsWith(USERS + "?username=")))
                .andRespond(withSuccess("[{\"id\":\"u-1\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USERS + "/u-1/reset-password"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalidPasswordMinLengthMessage\"}"));

        assertThatThrownBy(() -> client.setPassword(EMAIL, "Ricardo Salazar", "corta"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void expectInvalidGrant() {
        server.expect(requestTo(TOKEN)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user credentials\"}"));
    }

    private void expectBruteForceStatus(boolean disabled) {
        expectServiceAccountToken();
        server.expect(requestTo(startsWith(USERS + "?username=")))
                .andRespond(withSuccess("[{\"id\":\"u-1\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/admin/realms/medicalsys/attack-detection/brute-force/users/u-1"))
                .andRespond(withSuccess("{\"disabled\":" + disabled + ",\"numFailures\":3}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectServiceAccountToken() {
        server.expect(requestTo(TOKEN))
                .andExpect(content().formDataContains(java.util.Map.of("grant_type", "client_credentials")))
                .andRespond(withSuccess("{\"access_token\":\"admin\",\"expires_in\":300}",
                        MediaType.APPLICATION_JSON));
    }
}
