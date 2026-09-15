package com.medicalsys.notificaciones.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.repository.ConversationWindowStore;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MetaWhatsappProviderTest {

    @Mock ConversationWindowStore conversations;
    private AtomicReference<ClientRequest> captured;

    @BeforeEach
    void setUp() {
        captured = new AtomicReference<>();
    }

    @Test
    void rejectsMissingMetaCredentialsReactively() {
        MetaWhatsappProvider provider = provider("", "", success("wamid-1"));
        StepVerifier.create(provider.send(notification()))
                .expectErrorMessage("WHATSAPP_PHONE_NUMBER_ID y WHATSAPP_ACCESS_TOKEN son obligatorios para el proveedor meta")
                .verify();
    }

    @Test
    void sendsFreeTextWhenConversationWindowIsOpen() {
        Notification notification = notification();
        when(conversations.isOpen(notification.organizationId(), notification.patientId())).thenReturn(Mono.just(true));
        MetaWhatsappProvider provider = provider("phone-id", "token", success("wamid-text"));
        StepVerifier.create(provider.send(notification))
                .assertNext(result -> assertEquals("wamid-text", result.providerMessageId()))
                .verifyComplete();

        assertTrue(captured.get().url().getPath().endsWith("/v23.0/phone-id/messages"));
        assertEquals("Bearer token", captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void rejectsFreeTextOutsideTwentyFourHourWindow() {
        Notification notification = notification();
        when(conversations.isOpen(notification.organizationId(), notification.patientId())).thenReturn(Mono.just(false));
        MetaWhatsappProvider provider = provider("phone-id", "token", success("unused"));
        StepVerifier.create(provider.send(notification))
                .expectErrorMessage("Fuera de la ventana de 24 horas se requiere una plantilla aprobada por Meta")
                .verify();
    }

    @Test
    void rejectsSuccessfulMetaResponseWithoutMessageIdentifier() {
        Notification notification = notification();
        when(conversations.isOpen(notification.organizationId(), notification.patientId())).thenReturn(Mono.just(true));
        MetaWhatsappProvider provider = provider("phone-id", "token", successWithoutId());
        StepVerifier.create(provider.send(notification)).expectError().verify();
    }

    @Test
    void sendsExplicitManualTemplateThroughSender() {
        MetaWhatsappProvider provider = provider("phone-id", "token", success("wamid-manual"));
        StepVerifier.create(provider.sendTemplate("+591 700-00000", "appointment_created", "es", java.util.List.of("Ana")))
                .assertNext(result -> assertEquals("wamid-manual", result.providerMessageId()))
                .verifyComplete();
    }

    private MetaWhatsappProvider provider(String phoneId, String token, ClientResponse response) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(response);
        });
        return new MetaWhatsappProvider(builder, conversations,
                "https://graph.facebook.test", "v23.0", phoneId, token);
    }

    private ClientResponse success(String id) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"messages\":[{\"id\":\"" + id + "\"}]}")
                .build();
    }

    private ClientResponse successWithoutId() {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"messages\":[]}")
                .build();
    }

    private Notification notification() {
        LocalDateTime now = LocalDateTime.now();
        return new Notification(1L, "id", null, "key", "organization", "patient", null,
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, null, null, "EVENT", "ENTITY", null,
                "+591 700-00000", "{\"patientName\":\"Ana\"}", "Hola Ana", NotificationStatus.QUEUED,
                now, 0, null, null, null, null, null, null, null, null, now, now);
    }
}
