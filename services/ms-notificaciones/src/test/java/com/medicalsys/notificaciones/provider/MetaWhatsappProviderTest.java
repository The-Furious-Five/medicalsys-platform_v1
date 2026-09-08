package com.medicalsys.notificaciones.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.domain.TemplateApprovalStatus;
import com.medicalsys.notificaciones.repository.ConversationWindowStore;
import com.medicalsys.notificaciones.repository.MessageTemplateRepository;
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
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class MetaWhatsappProviderTest {

    @Mock MessageTemplateRepository templates;
    @Mock ConversationWindowStore conversations;
    private AtomicReference<ClientRequest> captured;

    @BeforeEach
    void setUp() {
        captured = new AtomicReference<>();
    }

    @Test
    void rejectsMissingMetaCredentialsReactively() {
        MetaWhatsappProvider provider = provider("", "", success("wamid-1"));
        StepVerifier.create(provider.send(notification(null)))
                .expectErrorMessage("WHATSAPP_PHONE_NUMBER_ID y WHATSAPP_ACCESS_TOKEN son obligatorios para el proveedor meta")
                .verify();
    }

    @Test
    void sendsApprovedMetaTemplateOutsideConversationWindow() {
        Notification notification = notification(7L);
        when(templates.findById(7L)).thenReturn(Mono.just(template(TemplateApprovalStatus.APPROVED,
                "appointment_created", "[\"patientName\"]")));
        MetaWhatsappProvider provider = provider("phone-id", "token", success("wamid-template"));

        StepVerifier.create(provider.send(notification))
                .assertNext(result -> assertEquals("wamid-template", result.providerMessageId()))
                .verifyComplete();

        assertTrue(captured.get().url().getPath().endsWith("/v23.0/phone-id/messages"));
        assertEquals("Bearer token", captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION));
        verify(conversations, never()).isOpen(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendsFreeTextWhenConversationWindowIsOpen() {
        Notification notification = notification(null);
        when(conversations.isOpen(notification.organizationId(), notification.patientId())).thenReturn(Mono.just(true));
        MetaWhatsappProvider provider = provider("phone-id", "token", success("wamid-text"));
        StepVerifier.create(provider.send(notification))
                .assertNext(result -> assertEquals("wamid-text", result.providerMessageId()))
                .verifyComplete();
    }

    @Test
    void rejectsFreeTextOutsideTwentyFourHourWindow() {
        Notification notification = notification(null);
        when(conversations.isOpen(notification.organizationId(), notification.patientId())).thenReturn(Mono.just(false));
        MetaWhatsappProvider provider = provider("phone-id", "token", success("unused"));
        StepVerifier.create(provider.send(notification))
                .expectErrorMessage("Fuera de la ventana de 24 horas se requiere una plantilla aprobada por Meta")
                .verify();
    }

    @Test
    void unapprovedTemplateFallsBackToFreeTextOnlyInsideWindow() {
        Notification notification = notification(7L);
        when(templates.findById(7L)).thenReturn(Mono.just(template(TemplateApprovalStatus.IN_REVIEW, null, "[]")));
        when(conversations.isOpen(notification.organizationId(), notification.patientId())).thenReturn(Mono.just(true));
        MetaWhatsappProvider provider = provider("phone-id", "token", success("wamid-text"));
        StepVerifier.create(provider.send(notification)).expectNextCount(1).verifyComplete();
    }

    @Test
    void rejectsInvalidStoredTemplateVariablesBeforeCallingMeta() {
        Notification notification = notification(7L);
        when(templates.findById(7L)).thenReturn(Mono.just(template(TemplateApprovalStatus.APPROVED,
                "appointment", "bad-json")));
        MetaWhatsappProvider provider = provider("phone-id", "token", success("unused"));
        StepVerifier.create(provider.send(notification))
                .expectErrorMessage("La plantilla no contiene variables JSON validas").verify();
    }

    @Test
    void rejectsSuccessfulMetaResponseWithoutMessageIdentifier() {
        Notification notification = notification(7L);
        when(templates.findById(7L)).thenReturn(Mono.just(template(TemplateApprovalStatus.APPROVED,
                "appointment", "[]")));
        MetaWhatsappProvider provider = provider("phone-id", "token", successWithoutId());
        StepVerifier.create(provider.send(notification)).expectError().verify();
    }

    private MetaWhatsappProvider provider(String phoneId, String token, ClientResponse response) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(response);
        });
        return new MetaWhatsappProvider(builder, templates, conversations, JsonMapper.builder().build(),
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

    private MessageTemplate template(TemplateApprovalStatus approval, String providerName, String variables) {
        LocalDateTime now = LocalDateTime.now();
        return new MessageTemplate(7L, "template", "organization", "APPOINTMENT",
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, providerName, "es", null,
                "Hola", variables, approval, true, now, now);
    }

    private Notification notification(Long templateId) {
        LocalDateTime now = LocalDateTime.now();
        return new Notification(1L, "id", null, "key", "organization", "patient", null,
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, templateId, null, "EVENT", "ENTITY", null,
                "+591 700-00000", "{\"patientName\":\"Ana\"}", "Hola Ana", NotificationStatus.QUEUED,
                now, 0, null, null, null, null, null, null, null, null, now, now);
    }
}
