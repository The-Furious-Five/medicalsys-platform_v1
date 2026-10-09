package com.medicalsys.notificaciones.repository;

import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationRule;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NotificationRuleRepository extends ReactiveCrudRepository<NotificationRule, Long> {
    Mono<NotificationRule> findByPublicIdAndOrganizationId(String publicId, String organizationId);
    Flux<NotificationRule> findAllByOrganizationIdOrderByEventTypeAsc(String organizationId);
    Flux<NotificationRule> findAllByOrganizationIdAndEventTypeAndChannelAndActiveTrue(
            String organizationId, String eventType, NotificationChannel channel);
}
