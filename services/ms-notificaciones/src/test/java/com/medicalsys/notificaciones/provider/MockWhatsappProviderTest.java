package com.medicalsys.notificaciones.provider;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class MockWhatsappProviderTest {

    @Test
    void returnsSimulatedWhatsappIdentifierWithoutExternalCall() {
        MockWhatsappProvider provider = new MockWhatsappProvider();
        StepVerifier.create(provider.send(notification()))
                .assertNext(result -> assertTrue(result.providerMessageId().startsWith("mock-wamid-")))
                .verifyComplete();
    }

    private Notification notification() {
        LocalDateTime now = LocalDateTime.now();
        return new Notification(1L, "id", null, "key", "org", "patient", null,
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, null, null, "EVENT", "ENTITY", null,
                "+59170000000", null, "Mensaje", NotificationStatus.QUEUED, now, 0, null, null, null, null,
                null, null, null, null, now, now);
    }
}
