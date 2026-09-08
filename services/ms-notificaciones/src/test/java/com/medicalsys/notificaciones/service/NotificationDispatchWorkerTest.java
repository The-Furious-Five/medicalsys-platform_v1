package com.medicalsys.notificaciones.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.api.dto.NotificationResponse;
import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.repository.NotificationRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchWorkerTest {

    @Mock NotificationRepository repository;
    @Mock NotificationService service;

    @Test
    void disabledWorkerDoesNotQueryQueue() {
        new NotificationDispatchWorker(repository, service, 3, 25, false).dispatchDueNotifications();
        verify(repository, never()).findDispatchable(any(), anyInt(), anyInt());
    }

    @Test
    void dispatchesEveryDueNotificationUsingConfiguredLimits() {
        Notification first = notification("one");
        Notification second = notification("two");
        when(repository.findDispatchable(any(LocalDateTime.class), eq(3), eq(10)))
                .thenReturn(Flux.just(first, second));
        when(service.dispatchAutomatically(first)).thenReturn(Mono.just(response("one")));
        when(service.dispatchAutomatically(second)).thenReturn(Mono.just(response("two")));

        new NotificationDispatchWorker(repository, service, 3, 10, true).dispatchDueNotifications();

        verify(service).dispatchAutomatically(first);
        verify(service).dispatchAutomatically(second);
    }

    @Test
    void workerContainsOneCycleFailureInsteadOfCrashingScheduler() {
        Notification value = notification("one");
        when(repository.findDispatchable(any(LocalDateTime.class), eq(3), eq(10))).thenReturn(Flux.just(value));
        when(service.dispatchAutomatically(value)).thenReturn(Mono.error(new IllegalStateException("failure")));
        new NotificationDispatchWorker(repository, service, 3, 10, true).dispatchDueNotifications();
        verify(service).dispatchAutomatically(value);
    }

    private Notification notification(String id) {
        LocalDateTime now = LocalDateTime.now();
        return new Notification(1L, id, null, id, "org", "patient", null,
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, null, null, "EVENT", "ENTITY", null,
                "+59170000000", null, "Mensaje", NotificationStatus.QUEUED, now, 0, null, null, null, null,
                null, null, null, null, now, now);
    }

    private NotificationResponse response(String id) {
        LocalDateTime now = LocalDateTime.now();
        return new NotificationResponse(id, null, "org", "patient", NotificationChannel.WHATSAPP,
                NotificationCategory.UTILITY, "EVENT", "ENTITY", null, "+59170000000", "Mensaje",
                NotificationStatus.SENT, now, 1, null, now, "wamid", null, null, now, now);
    }
}
