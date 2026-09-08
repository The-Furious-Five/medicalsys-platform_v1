package com.medicalsys.notificaciones.service;

import com.medicalsys.notificaciones.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDispatchWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchWorker.class);

    private final NotificationRepository repository;
    private final NotificationService service;
    private final int maxAttempts;
    private final int batchSize;
    private final boolean enabled;
    private final Clock clock = Clock.systemUTC();

    public NotificationDispatchWorker(NotificationRepository repository, NotificationService service,
            @Value("${medicalsys.notifications.max-attempts:3}") int maxAttempts,
            @Value("${medicalsys.notifications.worker-batch-size:25}") int batchSize,
            @Value("${medicalsys.notifications.worker-enabled:true}") boolean enabled) {
        this.repository = repository;
        this.service = service;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${medicalsys.notifications.worker-delay-ms:5000}")
    public void dispatchDueNotifications() {
        if (!enabled) {
            return;
        }
        repository.findDispatchable(LocalDateTime.now(clock), maxAttempts, batchSize)
                .concatMap(service::dispatchAutomatically)
                .doOnNext(value -> log.info("Notificacion {} procesada con estado {}", value.id(), value.status()))
                .doOnError(error -> log.error("Fallo el ciclo de despacho de notificaciones", error))
                .onErrorComplete()
                .subscribe();
    }
}
