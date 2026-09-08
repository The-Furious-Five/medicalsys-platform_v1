package com.medicalsys.notificaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.api.dto.CreateNotificationRequest;
import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationRule;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.domain.TemplateApprovalStatus;
import com.medicalsys.notificaciones.error.ApiException;
import com.medicalsys.notificaciones.messaging.NotificationDomainEvent;
import com.medicalsys.notificaciones.provider.NotificationProvider;
import com.medicalsys.notificaciones.provider.ProviderResult;
import com.medicalsys.notificaciones.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID NOTIFICATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 20, 0);

    @Mock NotificationRepository repository;
    @Mock NotificationProvider provider;
    @Mock ReferenceSyncService references;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T20:00:00Z"), ZoneOffset.UTC);
        service = new NotificationService(repository, provider, JsonMapper.builder().build(), references, clock, 3);
    }

    @Test
    void createsImmediateNotificationAndNormalizesEventNames() {
        when(repository.existsByIdempotencyKey("manual:" + EVENT_ID)).thenReturn(Mono.just(false));
        saveEcho();

        StepVerifier.create(service.create(ORGANIZATION_ID, request(EVENT_ID, null)))
                .assertNext(response -> {
                    assertEquals(NotificationStatus.QUEUED, response.status());
                    assertEquals("APPOINTMENT_CREATED", response.eventType());
                    assertEquals("APPOINTMENT", response.entityType());
                    assertEquals(NOW, response.scheduledFor());
                }).verifyComplete();
    }

    @Test
    void createsScheduledNotificationWhenDateIsInFuture() {
        LocalDateTime future = NOW.plusHours(2);
        when(repository.existsByIdempotencyKey("manual:" + EVENT_ID)).thenReturn(Mono.just(false));
        saveEcho();

        StepVerifier.create(service.create(ORGANIZATION_ID, request(EVENT_ID, future)))
                .assertNext(response -> {
                    assertEquals(NotificationStatus.SCHEDULED, response.status());
                    assertEquals(future, response.scheduledFor());
                }).verifyComplete();
    }

    @Test
    void createsManualNotificationsWithoutOriginUsingIndependentKeys() {
        when(repository.existsByIdempotencyKey(any())).thenReturn(Mono.just(false));
        saveEcho();

        StepVerifier.create(service.create(ORGANIZATION_ID, request(null, null))).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.create(ORGANIZATION_ID, request(null, null))).expectNextCount(1).verifyComplete();

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(repository, org.mockito.Mockito.times(2)).existsByIdempotencyKey(keys.capture());
        assertTrue(keys.getAllValues().stream().allMatch(value -> value.startsWith("notification:")));
        assertEquals(2, keys.getAllValues().stream().distinct().count());
    }

    @Test
    void serializesVariablesWhenCreating() {
        when(repository.existsByIdempotencyKey("manual:" + EVENT_ID)).thenReturn(Mono.just(false));
        saveEcho();
        service.create(ORGANIZATION_ID, request(EVENT_ID, null)).block();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().variables().contains("Paciente Demo"));
        assertEquals("manual:" + EVENT_ID, captor.getValue().idempotencyKey());
    }

    @Test
    void rejectsDuplicateManualOriginEvent() {
        when(repository.existsByIdempotencyKey("manual:" + EVENT_ID)).thenReturn(Mono.just(true));
        expectApiError(service.create(ORGANIZATION_ID, request(EVENT_ID, null)), "DUPLICATE_ORIGIN_EVENT");
        verify(repository, never()).save(any());
    }

    @Test
    void createsEventNotificationUsingTargetAndRuleOffset() {
        when(repository.existsByIdempotencyKey(EVENT_ID + ":rule-id")).thenReturn(Mono.just(false));
        saveEcho();
        StepVerifier.create(service.createFromEvent(event(NOW.plusDays(1), NOW.minusMinutes(5)),
                        rule(-60), template(), "Recordatorio"))
                .assertNext(response -> {
                    assertEquals(NotificationStatus.SCHEDULED, response.status());
                    assertEquals(NOW.plusDays(1).minusHours(1), response.scheduledFor());
                }).verifyComplete();
    }

    @Test
    void eventNotificationUsesOccurredAtWhenTargetIsMissing() {
        when(repository.existsByIdempotencyKey(EVENT_ID + ":rule-id")).thenReturn(Mono.just(false));
        saveEcho();
        StepVerifier.create(service.createFromEvent(event(null, NOW.plusMinutes(10)), rule(5), template(), "Mensaje"))
                .assertNext(response -> assertEquals(NOW.plusMinutes(15), response.scheduledFor()))
                .verifyComplete();
    }

    @Test
    void ignoresRepeatedEventRuleCombinationIdempotently() {
        when(repository.existsByIdempotencyKey(EVENT_ID + ":rule-id")).thenReturn(Mono.just(true));
        StepVerifier.create(service.createFromEvent(event(NOW, NOW), rule(0), template(), "Mensaje"))
                .verifyComplete();
        verify(repository, never()).save(any());
    }

    @Test
    void returnsNotificationBelongingToOrganization() {
        mockFind(notification(NotificationStatus.SENT, 1, NOW));
        StepVerifier.create(service.get(ORGANIZATION_ID, NOTIFICATION_ID))
                .assertNext(value -> assertEquals(NOTIFICATION_ID.toString(), value.id()))
                .verifyComplete();
    }

    @Test
    void returnsNotFoundForUnknownNotification() {
        when(repository.findByPublicIdAndOrganizationId(NOTIFICATION_ID.toString(), ORGANIZATION_ID.toString()))
                .thenReturn(Mono.empty());
        expectApiError(service.get(ORGANIZATION_ID, NOTIFICATION_ID), "NOTIFICATION_NOT_FOUND");
    }

    @Test
    void listsAllOrFiltersByStatus() {
        Notification sent = notification(NotificationStatus.SENT, 1, NOW);
        when(repository.findAllByOrganizationIdOrderByCreatedAtDesc(ORGANIZATION_ID.toString()))
                .thenReturn(Flux.just(sent));
        when(repository.findAllByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                ORGANIZATION_ID.toString(), List.of(NotificationStatus.SENT))).thenReturn(Flux.just(sent));
        StepVerifier.create(service.list(ORGANIZATION_ID, null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.list(ORGANIZATION_ID, NotificationStatus.SENT)).expectNextCount(1).verifyComplete();
    }

    @Test
    void rejectsDispatchOfFinalStatus() {
        mockFind(notification(NotificationStatus.DELIVERED, 1, NOW));
        expectApiError(service.dispatch(ORGANIZATION_ID, NOTIFICATION_ID), "NOTIFICATION_NOT_DISPATCHABLE");
        verify(provider, never()).send(any());
    }

    @Test
    void rejectsDispatchBeforeScheduledTime() {
        mockFind(notification(NotificationStatus.SCHEDULED, 0, NOW.plusMinutes(1)));
        expectApiError(service.dispatch(ORGANIZATION_ID, NOTIFICATION_ID), "NOTIFICATION_NOT_DUE");
    }

    @Test
    void rejectsDispatchWhenPatientDidNotOptIn() {
        Notification value = notification(NotificationStatus.QUEUED, 0, NOW.minusMinutes(1));
        mockFind(value);
        when(references.isAllowed(PATIENT_ID.toString(), NotificationChannel.WHATSAPP,
                NotificationCategory.UTILITY)).thenReturn(Mono.just(false));
        saveEcho();
        StepVerifier.create(service.dispatch(ORGANIZATION_ID, NOTIFICATION_ID))
                .assertNext(response -> {
                    assertEquals(NotificationStatus.REJECTED_OPT_OUT, response.status());
                    assertEquals("CONSENT_REQUIRED", response.errorCode());
                }).verifyComplete();
        verify(provider, never()).send(any());
    }

    @Test
    void dispatchesWhenPatientOptedIn() {
        Notification value = notification(NotificationStatus.QUEUED, 0, NOW.minusMinutes(1));
        mockFind(value);
        when(references.isAllowed(any(), any(), any())).thenReturn(Mono.just(true));
        when(provider.send(value)).thenReturn(Mono.just(new ProviderResult("wamid-1")));
        saveEcho();
        StepVerifier.create(service.dispatch(ORGANIZATION_ID, NOTIFICATION_ID))
                .assertNext(response -> {
                    assertEquals(NotificationStatus.SENT, response.status());
                    assertEquals("wamid-1", response.providerMessageId());
                    assertEquals(1, response.attempts());
                    assertEquals(NOW, response.sentAt());
                }).verifyComplete();
    }

    @Test
    void dispatchWithoutPatientDoesNotCheckConsent() {
        Notification value = withoutPatient(notification(NotificationStatus.QUEUED, 0, NOW));
        mockFind(value);
        when(provider.send(value)).thenReturn(Mono.just(new ProviderResult("wamid-user")));
        saveEcho();
        StepVerifier.create(service.dispatch(ORGANIZATION_ID, NOTIFICATION_ID))
                .assertNext(response -> assertEquals(NotificationStatus.SENT, response.status()))
                .verifyComplete();
        verify(references, never()).isAllowed(any(), any(), any());
    }

    @Test
    void providerFailureSchedulesExponentialRetry() {
        Notification value = notification(NotificationStatus.QUEUED, 0, NOW);
        mockFind(value);
        when(references.isAllowed(any(), any(), any())).thenReturn(Mono.just(true));
        when(provider.send(value)).thenReturn(Mono.error(new IllegalStateException("Meta no disponible")));
        saveEcho();
        StepVerifier.create(service.dispatch(ORGANIZATION_ID, NOTIFICATION_ID))
                .assertNext(response -> {
                    assertEquals(NotificationStatus.FAILED, response.status());
                    assertEquals(1, response.attempts());
                    assertEquals(NOW.plusMinutes(2), response.nextAttemptAt());
                    assertEquals("Meta no disponible", response.errorDetail());
                }).verifyComplete();
    }

    @Test
    void thirdFailureStopsAutomaticRetries() {
        Notification failedTwice = notification(NotificationStatus.FAILED, 2, NOW);
        Notification queued = failedTwice.queuedAgain(NOW);
        when(references.isAllowed(any(), any(), any())).thenReturn(Mono.just(true));
        when(provider.send(queued)).thenReturn(Mono.error(new RuntimeException()));
        saveEcho();
        StepVerifier.create(service.dispatchAutomatically(failedTwice))
                .assertNext(response -> {
                    assertEquals(3, response.attempts());
                    assertNull(response.nextAttemptAt());
                    assertEquals("El proveedor rechazo el envio", response.errorDetail());
                }).verifyComplete();
    }

    @Test
    void automaticallyRequeuesFailedNotificationBeforeSending() {
        Notification failed = notification(NotificationStatus.FAILED, 1, NOW.minusMinutes(1));
        Notification requeued = failed.queuedAgain(NOW);
        when(references.isAllowed(any(), any(), any())).thenReturn(Mono.just(true));
        when(provider.send(requeued)).thenReturn(Mono.just(new ProviderResult("wamid-retry")));
        saveEcho();
        StepVerifier.create(service.dispatchAutomatically(failed))
                .assertNext(response -> {
                    assertEquals(NotificationStatus.SENT, response.status());
                    assertEquals(2, response.attempts());
                }).verifyComplete();
    }

    @Test
    void manualRetryRequeuesFailedNotification() {
        mockFind(notification(NotificationStatus.FAILED, 1, NOW));
        saveEcho();
        StepVerifier.create(service.retry(ORGANIZATION_ID, NOTIFICATION_ID))
                .assertNext(response -> {
                    assertEquals(NotificationStatus.QUEUED, response.status());
                    assertNull(response.errorCode());
                }).verifyComplete();
    }

    @Test
    void retryRejectsNonFailedNotification() {
        mockFind(notification(NotificationStatus.SENT, 1, NOW));
        expectApiError(service.retry(ORGANIZATION_ID, NOTIFICATION_ID), "NOTIFICATION_NOT_FAILED");
    }

    @Test
    void retryRejectsWhenMaxAttemptsReached() {
        mockFind(notification(NotificationStatus.FAILED, 3, NOW));
        expectApiError(service.retry(ORGANIZATION_ID, NOTIFICATION_ID), "MAX_ATTEMPTS_REACHED");
    }

    private void expectApiError(org.reactivestreams.Publisher<?> publisher, String code) {
        StepVerifier.create(publisher).expectErrorSatisfies(error -> {
            ApiException apiError = assertInstanceOf(ApiException.class, error);
            assertEquals(code, apiError.code());
        }).verify();
    }

    private void mockFind(Notification value) {
        when(repository.findByPublicIdAndOrganizationId(NOTIFICATION_ID.toString(), ORGANIZATION_ID.toString()))
                .thenReturn(Mono.just(value));
    }

    private void saveEcho() {
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    private CreateNotificationRequest request(UUID eventId, LocalDateTime scheduledFor) {
        return new CreateNotificationRequest(eventId, PATIENT_ID, null, NotificationChannel.WHATSAPP,
                NotificationCategory.UTILITY, " appointment_created ", " appointment ", UUID.randomUUID(),
                "+59170000000", Map.of("patientName", "Paciente Demo"), "Mensaje", scheduledFor);
    }

    private NotificationDomainEvent event(LocalDateTime targetAt, LocalDateTime occurredAt) {
        return new NotificationDomainEvent(EVENT_ID, ORGANIZATION_ID, PATIENT_ID, " appointment_created ",
                " appointment ", UUID.randomUUID(), "+59170000000", Map.of("patientName", "Ana"),
                occurredAt, targetAt);
    }

    private NotificationRule rule(int offset) {
        return new NotificationRule(8L, "rule-id", ORGANIZATION_ID.toString(), "APPOINTMENT_CREATED",
                NotificationChannel.WHATSAPP, 7L, offset, null, null, true, NOW, NOW);
    }

    private MessageTemplate template() {
        return new MessageTemplate(7L, "template-id", ORGANIZATION_ID.toString(), "APPOINTMENT_CREATED",
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, "appointment_created", "es",
                null, "Hola {{patientName}}", "[\"patientName\"]", TemplateApprovalStatus.APPROVED,
                true, NOW, NOW);
    }

    private Notification notification(NotificationStatus status, int attempts, LocalDateTime scheduledFor) {
        return new Notification(1L, NOTIFICATION_ID.toString(), EVENT_ID.toString(), "manual:" + EVENT_ID,
                ORGANIZATION_ID.toString(), PATIENT_ID.toString(), null, NotificationChannel.WHATSAPP,
                NotificationCategory.UTILITY, null, null, "APPOINTMENT_CREATED", "APPOINTMENT", null,
                "+59170000000", null, "Mensaje", status, scheduledFor, attempts,
                status == NotificationStatus.FAILED ? NOW : null, null, null, null, null, null, null,
                null, NOW.minusHours(1), NOW.minusHours(1));
    }

    private Notification withoutPatient(Notification value) {
        return new Notification(value.id(), value.publicId(), value.originEventId(), value.idempotencyKey(),
                value.organizationId(), null, "user-id", value.channel(), value.category(), value.templateId(),
                value.ruleId(), value.eventType(), value.entityType(), value.entityId(), value.destination(),
                value.variables(), value.renderedBody(), value.status(), value.scheduledFor(), value.attempts(),
                value.nextAttemptAt(), value.sentAt(), value.deliveredAt(), value.readAt(), value.providerMessageId(),
                value.errorCode(), value.errorDetail(), value.estimatedCostUsd(), value.createdAt(), value.updatedAt());
    }
}
