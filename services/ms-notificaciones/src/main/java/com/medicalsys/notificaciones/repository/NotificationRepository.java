package com.medicalsys.notificaciones.repository;

import com.medicalsys.notificaciones.domain.Notification;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import java.util.Collection;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NotificationRepository extends ReactiveCrudRepository<Notification, Long> {

    Mono<Notification> findByPublicIdAndOrganizationId(String publicId, String organizationId);

    Mono<Boolean> existsByIdempotencyKey(String idempotencyKey);

    Mono<Notification> findByProviderMessageId(String providerMessageId);

    @org.springframework.data.r2dbc.repository.Query("""
            SELECT * FROM notification
            WHERE ((status IN ('QUEUED','SCHEDULED') AND scheduled_for <= :now)
                OR (status = 'FAILED' AND next_attempt_at IS NOT NULL AND next_attempt_at <= :now))
              AND attempts < :maxAttempts
            ORDER BY COALESCE(next_attempt_at, scheduled_for), id
            LIMIT :limit
            """)
    Flux<Notification> findDispatchable(java.time.LocalDateTime now, int maxAttempts, int limit);

    Flux<Notification> findAllByOrganizationIdAndStatusInOrderByCreatedAtDesc(
            String organizationId, Collection<NotificationStatus> statuses);

    Flux<Notification> findAllByOrganizationIdOrderByCreatedAtDesc(String organizationId);
}
