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
    private final JwtPropagationFilter filter = new JwtPropagationFilter(token -> TOKEN, redis);

    @Test
    @DisplayName("propaga los permisos del token en X-Permissions y reemplaza los que mando el cliente")
    void propagatesTokenPermissions() throws Exception {
        when(redis.hasKey("identity:session:test-jti")).thenReturn(true);
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

    private HttpServletRequest forward(MockHttpServletRequest request) throws Exception {
        AtomicReference<HttpServletRequest> forwarded = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> forwarded.set((HttpServletRequest) req));
        return forwarded.get();
    }
}
