package com.medicalsys.facturacion.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.facturacion.common.error.ForbiddenException;
import com.medicalsys.facturacion.common.error.UnauthenticatedException;
import com.medicalsys.facturacion.common.tenant.TenantContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/**
 * El permiso que el gateway propaga en X-Permissions decide el acceso; el rol ya no (RBAC-03). Cada
 * caso pasa por el TenantContextFilter real, asi que tambien prueba la lectura de los headers.
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
    @DisplayName("rechaza sin el permiso aunque el rol sea de administracion")
    void forbidsWithoutPermissionEvenForAdministrators() {
        MockHttpServletRequest request = request("otro.permiso.leer");
        request.addHeader(TenantContextFilter.ROLES_HEADER, "ADMINISTRATOR,PLATFORM_ADMIN");

        assertThatThrownBy(() -> authorize(request, "guarded"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining(PERMISSION);
    }

    @Test
    @DisplayName("un endpoint protegido exige la identidad propagada por el gateway")
    void protectedEndpointRequiresGatewayIdentity() {
        assertThatThrownBy(() -> authorize(new MockHttpServletRequest(), "guarded"))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    @DisplayName("un endpoint sin anotacion no se evalua aqui")
    void ignoresEndpointsWithoutAnnotation() throws Exception {
        assertThat(authorize(new MockHttpServletRequest(), "open")).isTrue();
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
    }
}
