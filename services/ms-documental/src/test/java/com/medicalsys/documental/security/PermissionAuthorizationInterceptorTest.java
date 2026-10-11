package com.medicalsys.documental.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/** El permiso que el gateway propaga en X-Permissions decide el acceso; el rol ya no (RBAC-03). */
class PermissionAuthorizationInterceptorTest {

    private static final String PERMISSION = "documents.document.read";

    private final PermissionAuthorizationInterceptor interceptor = new PermissionAuthorizationInterceptor();

    @Test
    @DisplayName("deja pasar cuando el token trae el permiso que exige el endpoint")
    void allowsWhenTokenCarriesThePermission() throws Exception {
        assertThat(interceptor.preHandle(request("documents.document.upload, " + PERMISSION),
                new MockHttpServletResponse(), handler("guarded"))).isTrue();
    }

    @Test
    @DisplayName("rechaza sin el permiso aunque el rol sea de administracion")
    void forbidsWithoutPermissionEvenForAdministrators() {
        MockHttpServletRequest request = request("documents.document.upload");
        request.addHeader("X-Roles", "ADMINISTRATOR,PLATFORM_ADMIN");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("guarded")))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN))
                .hasMessageContaining(PERMISSION);
    }

    @Test
    @DisplayName("un endpoint protegido exige la identidad propagada por el gateway")
    void protectedEndpointRequiresGatewayIdentity() {
        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                handler("guarded")))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    @DisplayName("deniega por defecto un endpoint sin anotacion, aunque la identidad traiga permisos")
    void deniesEndpointsWithoutAnnotation() {
        assertThatThrownBy(() -> interceptor.preHandle(request(PERMISSION), new MockHttpServletResponse(),
                handler("open")))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("deja pasar lo declarado publico, y lo interno solo bajo /internal/")
    void allowsPublicAndInternalDeclarations() throws Exception {
        assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/v1/documents/ping"),
                new MockHttpServletResponse(), handler("ping"))).isTrue();
        assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/internal/v1/documents/x"),
                new MockHttpServletResponse(), handler("internal"))).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest("GET", "/api/v1/documents"),
                new MockHttpServletResponse(), handler("internal")))
                .isInstanceOf(ApiException.class);
    }

    private static HandlerMethod handler(String name) throws NoSuchMethodException {
        return new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(name));
    }

    private static MockHttpServletRequest request(String permissions) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
        request.addHeader("X-Organization-Id", "00000000-0000-4000-8000-0000000000a1");
        request.addHeader("X-User-Id", "00000000-0000-4000-8000-000000000901");
        request.addHeader(PermissionAuthorizationInterceptor.PERMISSIONS_HEADER, permissions);
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
