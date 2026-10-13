package com.medicalsys.catalogos.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.catalogos.common.tenant.TenantContextFilter;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/**
 * El permiso que el gateway propaga en X-Permissions decide el acceso, y lo que no declara su control
 * se deniega (RBAC-04). Cada caso pasa por el TenantContextFilter real, asi que tambien prueba la
 * lectura de los headers.
 */
class PermissionAuthorizationInterceptorTest {

    private static final String PERMISSION = "modulo.recurso.accion";

    private final TenantContextFilter filter = new TenantContextFilter();
    private final PermissionAuthorizationInterceptor interceptor = new PermissionAuthorizationInterceptor();

    @Test
    @DisplayName("deja pasar cuando el token trae el permiso que exige el endpoint")
    void allowsWhenTokenCarriesThePermission() throws Exception {
        assertThat(authorize(request("otro.permiso.leer, " + PERMISSION), "guarded")).isTrue();
    }

    @Test
    @DisplayName("rechaza con 403 sin el permiso, y el mensaje lo nombra")
    void forbidsWithoutPermission() {
        assertThatThrownBy(() -> authorize(request("otro.permiso.leer"), "guarded"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining(PERMISSION);
    }

    @Test
    @DisplayName("un endpoint protegido exige la identidad propagada por el gateway")
    void protectedEndpointRequiresGatewayIdentity() {
        assertThatThrownBy(() -> authorize(new MockHttpServletRequest("GET", "/api/v1/prueba"), "guarded"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    @DisplayName("deniega por defecto un endpoint sin anotacion, aunque la identidad traiga permisos")
    void deniesEndpointsWithoutAnnotation() {
        assertThatThrownBy(() -> authorize(request(PERMISSION), "open"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("se deniega por defecto");
    }

    @Test
    @DisplayName("deja pasar lo declarado publico, y lo interno solo bajo /internal/")
    void allowsPublicAndInternalDeclarations() throws Exception {
        assertThat(authorize(new MockHttpServletRequest("GET", "/api/v1/prueba/ping"), "ping")).isTrue();
        assertThat(authorize(new MockHttpServletRequest("GET", "/internal/v1/prueba"), "internal")).isTrue();
        assertThatThrownBy(() -> authorize(new MockHttpServletRequest("GET", "/api/v1/prueba"), "internal"))
                .isInstanceOf(PermissionDeniedException.class);
    }

    private boolean authorize(MockHttpServletRequest request, String endpoint) throws Exception {
        HandlerMethod handler = new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(endpoint));
        AtomicBoolean allowed = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> allowed.set(
                interceptor.preHandle((HttpServletRequest) req, (HttpServletResponse) res, handler)));
        return allowed.get();
    }

    private static MockHttpServletRequest request(String permissions) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/prueba");
        request.addHeader(TenantContextFilter.ORGANIZATION_HEADER, "00000000-0000-4000-8000-0000000000a1");
        request.addHeader(TenantContextFilter.USER_HEADER, "00000000-0000-4000-8000-000000000901");
        request.addHeader(TenantContextFilter.PERMISSIONS_HEADER, permissions);
        return request;
    }

    static class Endpoints {

        @RequiresPermission(PERMISSION)
        public void guarded() {
        }

        public void open() {
        }

        @PublicEndpoint
        public void ping() {
        }

        @InternalEndpoint
        public void internal() {
        }
    }
}
