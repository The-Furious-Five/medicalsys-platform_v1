package com.medicalsys.identidad.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * LOG-01/LOG-02: verifica el cuerpo exacto que AuditTrail envia al RestClient hacia ms-auditoria,
 * sin levantar ese servicio: se mockea la cadena fluida de RestClient y se captura el body.
 */
@ExtendWith(MockitoExtension.class)
class AuditTrailTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private ExecutorService synchronousExecutor;

    @AfterEach
    void shutdown() {
        if (synchronousExecutor != null) {
            synchronousExecutor.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendsTheExpectedBodyAndHeaderForAnAuthenticationEvent() throws InterruptedException {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.header(anyString(), anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        AuditTrail auditTrail = newAuditTrail();
        UUID accountId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        auditTrail.record(AuditEventDraft.authentication("LOGIN_SUCCESS", "SUCCESS", accountId,
                "user@example.com", organizationId, 200, null));
        synchronousExecutor.shutdown();
        synchronousExecutor.awaitTermination(2, TimeUnit.SECONDS);

        ArgumentCaptor<String> header = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(requestBodyUriSpec).header(eq("X-Correlation-Id"), header.capture());

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(requestBodyUriSpec).body(bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();

        assertEquals("ms-identidad", body.get("serviceName"));
        assertEquals("LOGIN_SUCCESS", body.get("action"));
        assertEquals("AUTHENTICATION", body.get("category"));
        assertEquals("SUCCESS", body.get("outcome"));
        assertEquals(accountId, body.get("actorUserId"));
        assertEquals("user@example.com", body.get("actorDisplayName"));
        assertEquals(organizationId, body.get("organizationId"));
        assertEquals(200, body.get("responseStatus"));
        assertFalse(body.containsKey("correlationIdHint"));
    }

    @Test
    void doesNothingWhenAuditIsDisabled() {
        AuditTrailProperties disabled = new AuditTrailProperties("", null, null, null);
        AuditTrail auditTrail = new AuditTrail(restClient, Executors.newSingleThreadExecutor(), disabled);

        auditTrail.record(AuditEventDraft.authentication("LOGIN_FAILED", "DENIED", null, "x@example.com", null,
                401, "no existe"));

        org.mockito.Mockito.verifyNoInteractions(restClient);
    }

    private AuditTrail newAuditTrail() {
        synchronousExecutor = Executors.newSingleThreadExecutor();
        AuditTrailProperties properties = new AuditTrailProperties("http://ms-auditoria.local",
                Duration.ofSeconds(2), Duration.ofSeconds(5), 10);
        return new AuditTrail(restClient, synchronousExecutor, properties);
    }
}
