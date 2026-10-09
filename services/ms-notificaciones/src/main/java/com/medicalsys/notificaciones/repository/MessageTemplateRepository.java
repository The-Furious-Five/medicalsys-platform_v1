package com.medicalsys.notificaciones.repository;

import com.medicalsys.notificaciones.domain.MessageTemplate;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageTemplateRepository extends ReactiveCrudRepository<MessageTemplate, Long> {
    Mono<MessageTemplate> findByPublicIdAndOrganizationId(String publicId, String organizationId);
    Mono<Boolean> existsByOrganizationIdAndCodeAndLanguageCode(
            String organizationId, String code, String languageCode);
    Flux<MessageTemplate> findAllByOrganizationIdOrderByCodeAsc(String organizationId);
}
