package com.medicalsys.notificaciones.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.common.RequestOrganization;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * El permiso que el gateway propaga en X-Permissions decide el acceso, y lo que no declara su control
 * se deniega (RBAC-04). El filtro resuelve el controlador con el handler mapping, que aqui se simula.
 */
class PermissionAuthorizationWebFilterTest {

    private static final String PERMISSION = "notifications.whatsapp.send";

    private final ObjectMapper json = JsonMapper.builder().build();
    private final RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
    private final PermissionAuthorizationWebFilter filter = new PermissionAuthorizationWebFilter(mapping, json);

    @Test
    @DisplayName("deja pasar cuando el token trae el permiso que exige el endpoint")
    void allowsWhenTokenCarriesThePermission() throws Exception {
        String permissions = "otro.permiso, " + PERMISSION;
        Result result = run("guarded", identified("/api/v1/notifications/whatsapp/send", permissions));

        assertTrue(result.reachedController());
        assertNull(result.exchange().getResponse().getStatusCode());
    }

    @Test
    @DisplayName("rechaza con 403 sin el permiso y responde con el error comun del servicio")
    void forbidsWithoutPermission() throws Exception {
        Result result = run("guarded", identified("/api/v1/notifications/whatsapp/send", "otro.permiso"));

        assertFalse(result.reachedController());
        assertEquals(HttpStatus.FORBIDDEN, result.exchange().getResponse().getStatusCode());
        JsonNode body = json.readTree(result.exchange().getResponse().getBodyAsString().block());
        assertEquals("FORBIDDEN", body.get("code").asString());
        assertTrue(body.get("message").asString().contains(PERMISSION));
        assertEquals("/api/v1/notifications/whatsapp/send", body.get("path").asString());
    }

    @Test
    @DisplayName("un endpoint protegido exige la identidad propagada por el gateway")
    void protectedEndpointRequiresGatewayIdentity() throws Exception {
        MockServerHttpRequest anonymous = MockServerHttpRequest.post("/api/v1/notifications/whatsapp/send")
                .header(RequestOrganization.HEADER, "00000000-0000-4000-8000-0000000000a1")
                .header(PermissionAuthorizationWebFilter.PERMISSIONS_HEADER, PERMISSION)
                .build();

        Result result = run("guarded", anonymous);

        assertFalse(result.reachedController());
        assertEquals(HttpStatus.UNAUTHORIZED, result.exchange().getResponse().getStatusCode());
    }

    @Test
    @DisplayName("deniega por defecto un endpoint sin anotacion, aunque la identidad traiga permisos")
    void deniesEndpointsWithoutAnnotation() throws Exception {
        Result result = run("open", identified("/api/v1/notifications/nuevo", PERMISSION));

        assertFalse(result.reachedController());
        assertEquals(HttpStatus.FORBIDDEN, result.exchange().getResponse().getStatusCode());
    }

    @Test
    @DisplayName("deja pasar lo declarado publico, y lo interno solo bajo /internal/")
    void allowsPublicAndInternalDeclarations() throws Exception {
        assertTrue(run("webhook", MockServerHttpRequest.post("/api/v1/notifications/webhooks/whatsapp").build())
                .reachedController());
        assertTrue(run("internal", MockServerHttpRequest.post("/internal/v1/notifications/whatsapp/send").build())
                .reachedController());
        assertFalse(run("internal", MockServerHttpRequest.post("/api/v1/notifications/whatsapp/send").build())
                .reachedController());
    }

    @Test
    @DisplayName("una ruta sin controlador sigue de largo para que el dispatcher responda 404")
    void routesWithoutControllerPassThrough() {
        when(mapping.getHandler(any())).thenReturn(Mono.empty());
        AtomicBoolean reached = new AtomicBoolean();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/no-existe").build());

        filter.filter(exchange, next -> Mono.fromRunnable(() -> reached.set(true))).block();

        assertTrue(reached.get());
    }

    private Result run(String endpoint, MockServerHttpRequest request) throws Exception {
        HandlerMethod handler = new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(endpoint));
        when(mapping.getHandler(any())).thenReturn(Mono.just(handler));
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean reached = new AtomicBoolean();
        filter.filter(exchange, next -> Mono.fromRunnable(() -> reached.set(true))).block();
        return new Result(exchange, reached.get());
    }

    private static MockServerHttpRequest identified(String path, String permissions) {
        return MockServerHttpRequest.post(path)
                .header(RequestOrganization.HEADER, "00000000-0000-4000-8000-0000000000a1")
                .header(PermissionAuthorizationWebFilter.USER_HEADER, "00000000-0000-4000-8000-000000000901")
                .header(PermissionAuthorizationWebFilter.PERMISSIONS_HEADER, permissions)
                .build();
    }

    private record Result(MockServerWebExchange exchange, boolean reachedController) {
    }

    static class Endpoints {

        @RequiresPermission(PERMISSION)
        public void guarded() {
        }

        public void open() {
        }

        @PublicEndpoint
        public void webhook() {
        }

        @InternalEndpoint
        public void internal() {
        }
    }
}
