package com.medicalsys.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** LOG-02: que acciones asienta el gateway y como las describe. */
class GatewayAuditorTest {

    private static final String PATIENT = "11111111-2222-4333-8444-555555555555";
    private static final Jwt TOKEN = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("00000000-0000-4000-8000-000000000901")
            .claim("organizationId", "00000000-0000-4000-8000-0000000000a1")
            .claim("email", "osi@medicalsys.test")
            .claim("roles", List.of("OSI"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

    @Test
    @DisplayName("una escritura de cualquier servicio se asienta con servicio, categoria y desenlace")
    void recordsWrites() {
        Map<String, Object> body = GatewayAuditor.describe(TOKEN, "POST", "/api/v1/facturas", 201);

        assertThat(body).containsEntry("action", "API_CREATE").containsEntry("serviceName", "ms-facturacion")
                .containsEntry("category", "BILLING").containsEntry("outcome", "SUCCESS")
                .containsEntry("resourceType", "FACTURAS").containsEntry("actorDisplayName", "osi@medicalsys.test")
                .containsEntry("actorRoles", List.of("OSI"));
    }

    @Test
    @DisplayName("un borrado es advertencia y un error queda como FAILURE con su motivo")
    void deletesAndFailures() {
        assertThat(GatewayAuditor.describe(TOKEN, "DELETE", "/api/v1/campanas/" + PATIENT, 204))
                .containsEntry("action", "API_DELETE").containsEntry("severity", "WARNING")
                .containsEntry("resourcePublicId", UUID.fromString(PATIENT));
        assertThat(GatewayAuditor.describe(TOKEN, "PUT", "/api/v1/tenancy/organizations", 409))
                .containsEntry("outcome", "FAILURE").containsEntry("reason", "La operacion termino con HTTP 409");
    }

    @Test
    @DisplayName("la lectura de un paciente o de historia clinica se asienta; la de catalogos no")
    void recordsSensitiveReadsOnly() {
        assertThat(GatewayAuditor.describe(TOKEN, "GET", "/api/v1/patients/" + PATIENT + "/acceso", 200))
                .containsEntry("action", "API_READ").containsEntry("patientPublicId", UUID.fromString(PATIENT))
                .containsEntry("resourceType", "PATIENTS_ACCESO");
        assertThat(GatewayAuditor.describe(TOKEN, "GET", "/api/v1/encuentros/" + PATIENT, 200))
                .containsEntry("category", "CLINICAL_RECORD").containsEntry("severity", "NOTICE");
        assertThat(GatewayAuditor.describe(TOKEN, "GET", "/api/v1/catalogs/countries", 200)).isNull();
        assertThat(GatewayAuditor.describe(TOKEN, "GET", "/api/v1/audit/events", 200)).isNull();
    }

    @Test
    @DisplayName("un 403 es ACCESS_DENIED aunque sea una lectura; identidad, pings y anonimos no se asientan aqui")
    void deniedAndExclusions() {
        assertThat(GatewayAuditor.describe(TOKEN, "GET", "/api/v1/facturas", 403))
                .containsEntry("action", "ACCESS_DENIED").containsEntry("category", "AUTHORIZATION")
                .containsEntry("outcome", "DENIED");
        assertThat(GatewayAuditor.describe(TOKEN, "POST", "/api/v1/identity/users", 201)).isNull();
        assertThat(GatewayAuditor.describe(TOKEN, "POST", "/api/v1/identity/roles", 403)).isNull();
        assertThat(GatewayAuditor.describe(TOKEN, "GET", "/api/v1/billing/ping", 200)).isNull();
        assertThat(GatewayAuditor.describe(null, "POST", "/api/v1/facturas", 201)).isNull();
    }

    @Test
    @DisplayName("el tipo de recurso siempre cumple el formato que exige ms-auditoria")
    void resourceTypeMatchesContract() {
        assertThat(GatewayAuditor.resourceType("/api/v1/encuentros/" + PATIENT + "/signos-vitales"))
                .isEqualTo("ENCUENTROS_SIGNOS_VITALES");
        assertThat(GatewayAuditor.resourceType("/api/v1/")).isEqualTo("ENDPOINT");
        assertThat(GatewayAuditor.resourceType("/api/v1/a/b/c/d/e")).matches("^[A-Z][A-Z0-9_]{2,59}$");
    }
}
