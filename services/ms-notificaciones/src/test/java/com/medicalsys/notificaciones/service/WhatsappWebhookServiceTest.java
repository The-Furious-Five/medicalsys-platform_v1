package com.medicalsys.notificaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.api.dto.PreferenceReferenceRequest;
import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.error.ApiException;
import com.medicalsys.notificaciones.repository.NotificationRepository;
import com.medicalsys.notificaciones.repository.WhatsappWebhookStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class WhatsappWebhookServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 20, 0);
    private static final String PATIENT_ID = "20000000-0000-0000-0000-000000000001";
    private static final String ORGANIZATION_ID = "10000000-0000-0000-0000-000000000001";

    @Mock NotificationRepository notifications;
    @Mock ReferenceSyncService references;
    @Mock WhatsappWebhookStore store;
    private WhatsappWebhookService service;

    @BeforeEach
    void setUp() {
        service = createService("");
    }

    @Test
    void verifiesMetaChallengeWithCorrectToken() {
        assertEquals("challenge-123", service.verify("subscribe", "verify-token", "challenge-123"));
    }

    @Test
    void rejectsWrongVerificationMode() {
        ApiException error = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
                () -> service.verify("unsubscribe", "verify-token", "challenge"));
        assertEquals("WEBHOOK_VERIFICATION_FAILED", error.code());
    }

    @Test
    void rejectsWrongVerificationToken() {
        ApiException error = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
                () -> service.verify("subscribe", "wrong", "challenge"));
        assertEquals("WEBHOOK_VERIFICATION_FAILED", error.code());
    }

    @Test
    void rejectsMalformedJson() {
        StepVerifier.create(service.handle("not-json", null))
                .expectErrorSatisfies(error -> assertEquals("INVALID_WEBHOOK_PAYLOAD",
                        assertInstanceOf(ApiException.class, error).code()))
                .verify();
    }

    @Test
    void requiresSignatureWhenAppSecretIsConfigured() {
        WhatsappWebhookService signed = createService("secret");
        StepVerifier.create(signed.handle("{}", null))
                .expectErrorSatisfies(error -> assertEquals("INVALID_WEBHOOK_SIGNATURE",
                        assertInstanceOf(ApiException.class, error).code()))
                .verify();
    }

    @Test
    void rejectsInvalidSignature() {
        WhatsappWebhookService signed = createService("secret");
        StepVerifier.create(signed.handle("{}", "sha256=bad"))
                .expectErrorSatisfies(error -> assertEquals("INVALID_WEBHOOK_SIGNATURE",
                        assertInstanceOf(ApiException.class, error).code()))
                .verify();
    }

    @Test
    void acceptsValidHmacSignature() throws Exception {
        String payload = "{\"entry\":[]}";
        WhatsappWebhookService signed = createService("secret");
        StepVerifier.create(signed.handle(payload, hmac(payload, "secret"))).verifyComplete();
    }

    @Test
    void updatesDeliveredStatusAndStoresRawEvent() {
        Notification original = notification(NotificationStatus.SENT);
        when(notifications.findByProviderMessageId("wamid-1")).thenReturn(Mono.just(original));
        when(notifications.save(any(Notification.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(store.insertStatusEvent(any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.handle(statusPayload("delivered", "wamid-1", null), null)).verifyComplete();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertEquals(NotificationStatus.DELIVERED, captor.getValue().status());
        assertEquals(LocalDateTime.of(2023, 11, 14, 22, 13, 20), captor.getValue().deliveredAt());
        verify(store).insertStatusEvent(eq(1L), eq("DELIVERED"), any(), eq(null), anyString());
    }

    @Test
    void updatesReadStatus() {
        Notification original = notification(NotificationStatus.DELIVERED);
        when(notifications.findByProviderMessageId("wamid-1")).thenReturn(Mono.just(original));
        when(notifications.save(any(Notification.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(store.insertStatusEvent(any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        StepVerifier.create(service.handle(statusPayload("read", "wamid-1", null), null)).verifyComplete();
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertEquals(NotificationStatus.READ, captor.getValue().status());
        assertEquals(LocalDateTime.of(2023, 11, 14, 22, 13, 20), captor.getValue().readAt());
    }

    @Test
    void failedStatusCapturesProviderErrorAndSchedulesRetry() {
        Notification original = notification(NotificationStatus.SENT);
        when(notifications.findByProviderMessageId("wamid-1")).thenReturn(Mono.just(original));
        when(notifications.save(any(Notification.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(store.insertStatusEvent(any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        StepVerifier.create(service.handle(statusPayload("failed", "wamid-1", "131047"), null)).verifyComplete();
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertEquals(NotificationStatus.FAILED, captor.getValue().status());
        assertEquals("131047", captor.getValue().errorCode());
        assertEquals(NOW.plusMinutes(2), captor.getValue().nextAttemptAt());
    }

    @Test
    void ignoresUnknownStatusAndUnknownProviderMessage() {
        StepVerifier.create(service.handle(statusPayload("mystery", "wamid-1", null), null)).verifyComplete();
        verify(notifications, never()).findByProviderMessageId(any());

        when(notifications.findByProviderMessageId("wamid-2")).thenReturn(Mono.empty());
        StepVerifier.create(service.handle(statusPayload("sent", "wamid-2", null), null)).verifyComplete();
        verify(notifications, never()).save(any());
        verify(store, never()).insertStatusEvent(any(), any(), any(), any(), any());
    }

    @Test
    void associatesKnownIncomingPatientAndOpensConversationOnce() {
        when(references.findPatientByWhatsapp("+59170000000"))
                .thenReturn(Mono.just(new ReferenceSyncService.PatientTarget(PATIENT_ID, ORGANIZATION_ID)));
        when(store.insertIncoming(any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(store.openConversation(any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.handle(messagePayload("¿Cuándo es mi cita?"), null)).verifyComplete();

        verify(store).insertIncoming("incoming-1", "+59170000000", "¿Cuándo es mi cita?", "QUESTION",
                LocalDateTime.of(2023, 11, 14, 22, 13, 20), ORGANIZATION_ID, PATIENT_ID);
        verify(store).openConversation("+59170000000", LocalDateTime.of(2023, 11, 14, 22, 13, 20),
                ORGANIZATION_ID, PATIENT_ID);
        verify(store, org.mockito.Mockito.times(1)).insertIncoming(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void storesUnknownPhoneWithoutOpeningConversation() {
        when(references.findPatientByWhatsapp("+59170000000")).thenReturn(Mono.empty());
        when(store.insertIncoming(any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        StepVerifier.create(service.handle(messagePayload("Hola"), null)).verifyComplete();
        verify(store).insertIncoming("incoming-1", "+59170000000", "Hola", "OTHER",
                LocalDateTime.of(2023, 11, 14, 22, 13, 20), null, null);
        verify(store, never()).openConversation(any(), any(), any(), any());
    }

    @Test
    void stopMessageRevokesOnlyMarketingConsent() {
        when(references.findPatientByWhatsapp("+59170000000"))
                .thenReturn(Mono.just(new ReferenceSyncService.PatientTarget(PATIENT_ID, ORGANIZATION_ID)));
        when(store.insertIncoming(any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(store.openConversation(any(), any(), any(), any())).thenReturn(Mono.empty());
        when(references.upsertPreference(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.handle(messagePayload("STOP"), null)).verifyComplete();

        ArgumentCaptor<PreferenceReferenceRequest> captor = ArgumentCaptor.forClass(PreferenceReferenceRequest.class);
        verify(references).upsertPreference(captor.capture());
        assertEquals(NotificationCategory.MARKETING, captor.getValue().category());
        assertEquals(NotificationChannel.WHATSAPP, captor.getValue().channel());
        assertEquals(false, captor.getValue().optedIn());
        assertEquals(NOW, captor.getValue().optedOutAt());
        verify(store).insertIncoming(any(), any(), any(), eq("MARKETING_OPT_OUT"), any(), any(), any());
    }

    @Test
    void ignoresIncomingMessageMissingIdOrSource() {
        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{\"from\":\"59170000000\"}]}}]}]}";
        StepVerifier.create(service.handle(payload, null)).verifyComplete();
        verify(references, never()).findPatientByWhatsapp(any());
        verify(store, never()).insertIncoming(any(), any(), any(), any(), any(), any(), any());
    }

    private WhatsappWebhookService createService(String secret) {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T20:00:00Z"), ZoneOffset.UTC);
        return new WhatsappWebhookService(JsonMapper.builder().build(), notifications, references, store,
                "verify-token", secret, clock);
    }

    private String hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private String statusPayload(String status, String id, String errorCode) {
        String errors = errorCode == null ? "" : ",\"errors\":[{\"code\":\"" + errorCode + "\"}]";
        return "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"" + id
                + "\",\"status\":\"" + status + "\",\"timestamp\":\"1700000000\"" + errors
                + "}]}}]}]}";
    }

    private String messagePayload(String body) {
        return "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{\"id\":\"incoming-1\","
                + "\"from\":\"591 700-00000\",\"timestamp\":\"1700000000\","
                + "\"text\":{\"body\":\"" + body + "\"}}]}}]}]}";
    }

    private Notification notification(NotificationStatus status) {
        return new Notification(1L, UUID.randomUUID().toString(), null, "key", ORGANIZATION_ID, PATIENT_ID,
                null, NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, null, null, "EVENT", "ENTITY",
                null, "+59170000000", null, "Mensaje", status, NOW.minusMinutes(1), 1, null, NOW.minusMinutes(2),
                null, null, "wamid-1", null, null, null, NOW.minusHours(1), NOW.minusHours(1));
    }
}
