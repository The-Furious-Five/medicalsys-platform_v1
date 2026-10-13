package com.medicalsys.notificaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.api.dto.SendWhatsappMessageRequest;
import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.error.ApiException;
import com.medicalsys.notificaciones.provider.ProviderResult;
import com.medicalsys.notificaciones.provider.WhatsappMessageSender;
import com.medicalsys.notificaciones.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Consola manual de WhatsApp: que valida, que deja registrado y que hace si el proveedor falla. */
@ExtendWith(MockitoExtension.class)
class WhatsappManualMessageServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String DESTINATION = "+59170000000";

    @Mock NotificationRepository repository;
    @Mock WhatsappMessageSender sender;

    private WhatsappManualMessageService service;

    @BeforeEach
    void setUp() {
        service = new WhatsappManualMessageService(repository, sender);
    }

    @Test
    void rejectsRequestWithoutTextOrTemplate() {
        StepVerifier.create(service.send(ORGANIZATION_ID, request("   ", null, null, null)))
                .expectErrorSatisfies(error -> assertApiError(error, "INVALID_WHATSAPP_MESSAGE"))
                .verify();
        verifyNoInteractions(repository, sender);
    }

    @Test
    void rejectsRequestWithBothTextAndTemplate() {
        StepVerifier.create(service.send(ORGANIZATION_ID, request("Hola", "recordatorio", "es", null)))
                .expectErrorSatisfies(error -> assertApiError(error, "INVALID_WHATSAPP_MESSAGE"))
                .verify();
        verifyNoInteractions(repository, sender);
    }

    @Test
    void templateRequiresLanguage() {
        StepVerifier.create(service.send(ORGANIZATION_ID, request(null, "recordatorio", " ", null)))
                .expectErrorSatisfies(error -> assertApiError(error, "TEMPLATE_LANGUAGE_REQUIRED"))
                .verify();
        verifyNoInteractions(repository, sender);
    }

    @Test
    void recordsQueuedNotificationBeforeSendingTrimmedTextAndMarksItSent() {
        saveEcho();
        when(sender.sendText(DESTINATION, "Hola")).thenReturn(Mono.just(new ProviderResult("wamid.1")));

        StepVerifier.create(service.send(ORGANIZATION_ID, request("  Hola  ", null, null, null)))
                .assertNext(response -> {
                    assertEquals(NotificationStatus.SENT, response.status());
                    assertEquals("wamid.1", response.providerMessageId());
                    assertEquals(1, response.attempts());
                })
                .verifyComplete();

        List<Notification> saved = savedNotifications(2);
        Notification queued = saved.get(0);
        assertEquals(NotificationStatus.QUEUED, queued.status());
        assertEquals(NotificationChannel.WHATSAPP, queued.channel());
        assertEquals(ORGANIZATION_ID.toString(), queued.organizationId());
        assertEquals(DESTINATION, queued.destination());
        assertEquals("Hola", queued.renderedBody());
        assertEquals(NotificationStatus.SENT, saved.get(1).status());
    }

    @Test
    void sendsTemplateWithEmptyParametersWhenNoneAreGiven() {
        saveEcho();
        when(sender.sendTemplate(DESTINATION, "recordatorio", "es", List.of()))
                .thenReturn(Mono.just(new ProviderResult("wamid.2")));

        StepVerifier.create(service.send(ORGANIZATION_ID, request(null, "recordatorio", "es", null)))
                .assertNext(response -> assertEquals(NotificationStatus.SENT, response.status()))
                .verifyComplete();

        assertEquals("Plantilla: recordatorio", savedNotifications(2).get(0).renderedBody());
    }

    @Test
    void providerFailureIsRecordedWithDetailCappedAt500Characters() {
        saveEcho();
        when(sender.sendText(DESTINATION, "Hola")).thenReturn(Mono.error(new IllegalStateException("x".repeat(600))));

        StepVerifier.create(service.send(ORGANIZATION_ID, request("Hola", null, null, null)))
                .assertNext(response -> {
                    assertEquals(NotificationStatus.FAILED, response.status());
                    assertEquals("PROVIDER_ERROR", response.errorCode());
                    assertEquals("x".repeat(500), response.errorDetail());
                })
                .verifyComplete();
    }

    @Test
    void providerFailureWithoutMessageGetsReadableDetail() {
        saveEcho();
        when(sender.sendText(DESTINATION, "Hola")).thenReturn(Mono.error(new IllegalStateException()));

        StepVerifier.create(service.send(ORGANIZATION_ID, request("Hola", null, null, null)))
                .assertNext(response -> assertEquals("El proveedor rechazo el envio", response.errorDetail()))
                .verifyComplete();
    }

    private static SendWhatsappMessageRequest request(String message, String template, String language,
                                                      List<String> parameters) {
        return new SendWhatsappMessageRequest(DESTINATION, message, template, language, parameters);
    }

    private static void assertApiError(Throwable error, String code) {
        ApiException apiError = assertInstanceOf(ApiException.class, error);
        assertEquals(HttpStatus.BAD_REQUEST, apiError.status());
        assertEquals(code, apiError.code());
    }

    private void saveEcho() {
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    private List<Notification> savedNotifications(int expected) {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }
}
