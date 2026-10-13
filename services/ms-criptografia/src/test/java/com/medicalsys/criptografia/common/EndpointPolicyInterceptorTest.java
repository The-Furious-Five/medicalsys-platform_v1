package com.medicalsys.criptografia.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

/** ms-criptografia solo atiende lo que se declara interno o publico (RBAC-04). */
class EndpointPolicyInterceptorTest {

    private final EndpointPolicyInterceptor interceptor = new EndpointPolicyInterceptor();

    @Test
    @DisplayName("deja pasar lo interno bajo /internal/ y lo publico")
    void allowsDeclaredEndpoints() throws Exception {
        assertThat(allows("/internal/v1/cifrado", "internal")).isTrue();
        assertThat(allows("/api/v1/cryptography/ping", "ping")).isTrue();
    }

    @Test
    @DisplayName("deniega por defecto lo no declarado y lo interno publicado fuera de /internal/")
    void deniesEverythingElse() {
        assertThatThrownBy(() -> allows("/internal/v1/nuevo", "open"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getReason()).isEqualTo("ENDPOINT_NOT_DECLARED"));
        assertThatThrownBy(() -> allows("/api/v1/cryptography/cifrado", "internal"))
                .isInstanceOf(ResponseStatusException.class);
    }

    private boolean allows(String uri, String endpoint) throws Exception {
        HandlerMethod handler = new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(endpoint));
        return interceptor.preHandle(new MockHttpServletRequest("POST", uri), new MockHttpServletResponse(), handler);
    }

    static class Endpoints {

        @InternalEndpoint
        public void internal() {
        }

        @PublicEndpoint
        public void ping() {
        }

        public void open() {
        }
    }
}
