package com.medicalsys.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Los servicios reciben los permisos del JWT validado, nunca los que mande el cliente (RBAC-03). */
class JwtPropagationFilterTest {

    private static final Jwt TOKEN = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("00000000-0000-4000-8000-000000000901")
            .claim("jti", "test-jti")
            .claim("organizationId", "00000000-0000-4000-8000-0000000000a1")
            .claim("roles", List.of("RECEPTIONIST"))
            .claim("permissions", List.of("patients.patient.read", "billing.invoice.read"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AccessDeniedAuditor auditor = org.mockito.Mockito.mock(AccessDeniedAuditor.class);
    private final JwtPropagationFilter filter = new JwtPropagationFilter(token -> TOKEN, redis, auditor);

    @Test
    @DisplayName("propaga los permisos del token en X-Permissions y reemplaza los que mando el cliente")
    void propagatesTokenPermissions() throws Exception {
        when(redis.hasKey("identity:session:v10:test-jti")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        request.addHeader("X-Permissions", "identity.role.manage");

        HttpServletRequest forwarded = forward(request);

        assertThat(forwarded.getHeader("X-Permissions")).isEqualTo("patients.patient.read,billing.invoice.read");
        assertThat(Collections.list(forwarded.getHeaders("X-Permissions"))).hasSize(1);
        assertThat(forwarded.getHeader("X-Roles")).isEqualTo("RECEPTIONIST");
    }

    @Test
    @DisplayName("rechaza un token cuya sesion ya no existe en Redis")
    void rejectsRevokedSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("La peticion no debe llegar al servicio");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Cross-Origin-Resource-Policy")).isEqualTo("same-origin");
    }

    @Test
    @DisplayName("si Redis no responde, falla cerrado con 503 en vez de dejar pasar el token")
    void failsClosedWhenRedisIsUnavailable() throws Exception {
        when(redis.hasKey("identity:session:v10:test-jti")).thenThrow(new QueryTimeoutException("timeout"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("La peticion no debe llegar al servicio");
        });

        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("sin token, un X-Permissions inyectado por el cliente no llega a los servicios")
    void dropsInjectedPermissionsWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Permissions", "identity.role.manage");

        HttpServletRequest forwarded = forward(request);

        assertThat(forwarded.getHeader("X-Permissions")).isNull();
        assertThat(Collections.list(forwarded.getHeaderNames())).doesNotContain("X-Permissions");
    }

    @Test
    @DisplayName("LOG-01: detras del proxy interno, la IP del usuario sale del X-Forwarded-For")
    void forwardsTheClientIpGivenByTheInternalProxy() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.19.0.12");
        request.addHeader("X-Forwarded-For", "181.115.40.7, 172.19.0.3");

        assertThat(forward(request).getHeader("X-Client-Ip")).isEqualTo("181.115.40.7");
    }

    @Test
    @DisplayName("LOG-01: un cliente que entra directo desde internet no puede falsificar su IP")
    void ignoresAForgedForwardedForFromAPublicClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("200.87.12.34");
        request.addHeader("X-Forwarded-For", "10.0.0.1");

        assertThat(forward(request).getHeader("X-Client-Ip")).isEqualTo("200.87.12.34");
    }

    @Test
    @DisplayName("LOG-01: sin X-Forwarded-For, la IP es la de la conexion")
    void usesTheConnectionAddressWithoutForwardedFor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.19.0.12");

        assertThat(forward(request).getHeader("X-Client-Ip")).isEqualTo("172.19.0.12");
    }

    @Test
    @DisplayName("LOG-02: un 403 de un servicio a un usuario autenticado se registra como ACCESS_DENIED")
    void recordsAccessDeniedFromDownstreamServices() throws Exception {
        when(redis.hasKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/facturas");
        request.addHeader("Authorization", "Bearer token");

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(403));

        org.mockito.Mockito.verify(auditor).record(org.mockito.ArgumentMatchers.eq(TOKEN),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("LOG-02: no se registran los 403 de identidad (los audita ese servicio) ni las respuestas 200")
    void skipsIdentityDenialsAndSuccessfulResponses() {
        assertThat(AccessDeniedAuditor.shouldRecord(TOKEN, 403, "/api/v1/identity/roles")).isFalse();
        assertThat(AccessDeniedAuditor.shouldRecord(TOKEN, 200, "/api/v1/facturas")).isFalse();
        assertThat(AccessDeniedAuditor.shouldRecord(null, 403, "/api/v1/facturas")).isFalse();
        assertThat(AccessDeniedAuditor.shouldRecord(TOKEN, 403, "/api/v1/facturas")).isTrue();
    }

    @Test
    @DisplayName("LOG-01: un X-Client-Ip enviado por el cliente se descarta")
    void dropsAClientIpSentByTheClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("200.87.12.34");
        request.addHeader("X-Client-Ip", "10.0.0.1");

        HttpServletRequest forwarded = forward(request);

        assertThat(forwarded.getHeader("X-Client-Ip")).isEqualTo("200.87.12.34");
        assertThat(Collections.list(forwarded.getHeaders("X-Client-Ip"))).containsExactly("200.87.12.34");
    }

    private HttpServletRequest forward(MockHttpServletRequest request) throws Exception {
        AtomicReference<HttpServletRequest> forwarded = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> forwarded.set((HttpServletRequest) req));
        return forwarded.get();
    }
}
