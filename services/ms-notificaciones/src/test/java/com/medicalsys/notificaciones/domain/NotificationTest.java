package com.medicalsys.notificaciones.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 9, 7, 18, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 20, 0);

    @Test
    void queuedAgainClearsErrorsAndPreservesAttempts() {
        Notification result = notification(NotificationStatus.FAILED, 2).queuedAgain(NOW);
        assertEquals(NotificationStatus.QUEUED, result.status());
        assertEquals(2, result.attempts());
        assertEquals(NOW, result.scheduledFor());
        assertNull(result.nextAttemptAt());
        assertNull(result.errorCode());
        assertNull(result.errorDetail());
    }

    @Test
    void sentIncrementsAttemptsAndStoresProviderId() {
        Notification result = notification(NotificationStatus.QUEUED, 0).sent("wamid-1", NOW);
        assertEquals(NotificationStatus.SENT, result.status());
        assertEquals(1, result.attempts());
        assertEquals("wamid-1", result.providerMessageId());
        assertEquals(NOW, result.sentAt());
    }

    @Test
    void failedIncrementsAttemptsAndStoresRetryDetails() {
        Notification result = notification(NotificationStatus.QUEUED, 1)
                .failed("TIMEOUT", "Proveedor lento", NOW.plusMinutes(4), NOW);
        assertEquals(NotificationStatus.FAILED, result.status());
        assertEquals(2, result.attempts());
        assertEquals("TIMEOUT", result.errorCode());
        assertEquals(NOW.plusMinutes(4), result.nextAttemptAt());
    }

    @Test
    void deliveredStatusSetsOnlyDeliveredTimestamp() {
        Notification result = notification(NotificationStatus.SENT, 1)
                .providerStatus(NotificationStatus.DELIVERED, NOW, null, null, NOW);
        assertEquals(NOW, result.deliveredAt());
        assertNull(result.readAt());
    }

    @Test
    void readStatusSetsReadTimestampAndPreservesDelivered() {
        Notification delivered = notification(NotificationStatus.SENT, 1)
                .providerStatus(NotificationStatus.DELIVERED, NOW.minusMinutes(1), null, null, NOW.minusMinutes(1));
        Notification read = delivered.providerStatus(NotificationStatus.READ, NOW, null, null, NOW);
        assertEquals(NOW.minusMinutes(1), read.deliveredAt());
        assertEquals(NOW, read.readAt());
    }

    private Notification notification(NotificationStatus status, int attempts) {
        return new Notification(1L, "public-id", "event-id", "key", "organization-id", "patient-id", null,
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, null, null, "EVENT", "ENTITY", null,
                "+59170000000", null, "Mensaje", status, CREATED, attempts,
                status == NotificationStatus.FAILED ? NOW : null, null, null, null, null,
                status == NotificationStatus.FAILED ? "ERROR" : null,
                status == NotificationStatus.FAILED ? "detail" : null, null, CREATED, CREATED);
    }
}
