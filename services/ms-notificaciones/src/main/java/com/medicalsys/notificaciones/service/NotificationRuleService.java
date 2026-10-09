package com.medicalsys.notificaciones.service;

import com.medicalsys.notificaciones.api.dto.NotificationRuleRequest;
import com.medicalsys.notificaciones.api.dto.NotificationRuleResponse;
import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.domain.NotificationRule;
import com.medicalsys.notificaciones.error.ApiException;
import com.medicalsys.notificaciones.repository.MessageTemplateRepository;
import com.medicalsys.notificaciones.repository.NotificationRuleRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class NotificationRuleService {

    private final NotificationRuleRepository rules;
    private final MessageTemplateRepository templates;
    private final Clock clock;

    @Autowired
    public NotificationRuleService(NotificationRuleRepository rules, MessageTemplateRepository templates) {
        this(rules, templates, Clock.systemUTC());
    }

    NotificationRuleService(NotificationRuleRepository rules, MessageTemplateRepository templates, Clock clock) {
        this.rules = rules;
        this.templates = templates;
        this.clock = clock;
    }

    public Mono<NotificationRuleResponse> create(UUID organizationId, NotificationRuleRequest request) {
        return template(organizationId, request.templateId()).flatMap(template -> {
            LocalDateTime now = LocalDateTime.now(clock);
            NotificationRule rule = new NotificationRule(null, UUID.randomUUID().toString(),
                    organizationId.toString(), normalize(request.eventType()), request.channel(), template.id(),
                    request.offsetMinutes(), request.sendingWindowFrom(), request.sendingWindowTo(),
                    request.active(), now, now);
            return rules.save(rule).map(saved -> NotificationRuleResponse.from(saved, template.publicId()));
        });
    }

    public Flux<NotificationRuleResponse> list(UUID organizationId) {
        return rules.findAllByOrganizationIdOrderByEventTypeAsc(organizationId.toString())
                .concatMap(rule -> templates.findById(rule.templateId())
                        .map(template -> NotificationRuleResponse.from(rule, template.publicId())));
    }

    public Mono<NotificationRuleResponse> update(UUID organizationId, UUID ruleId,
            NotificationRuleRequest request) {
        return Mono.zip(find(organizationId, ruleId), template(organizationId, request.templateId()))
                .flatMap(values -> {
                    NotificationRule updated = values.getT1().update(normalize(request.eventType()), request.channel(),
                            values.getT2().id(), request.offsetMinutes(), request.sendingWindowFrom(),
                            request.sendingWindowTo(), request.active(), LocalDateTime.now(clock));
                    return rules.save(updated)
                            .map(saved -> NotificationRuleResponse.from(saved, values.getT2().publicId()));
                });
    }

    private Mono<NotificationRule> find(UUID organizationId, UUID ruleId) {
        return rules.findByPublicIdAndOrganizationId(ruleId.toString(), organizationId.toString())
                .switchIfEmpty(Mono.error(new ApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND",
                        "No existe la regla solicitada en esta organizacion")));
    }

    private Mono<MessageTemplate> template(UUID organizationId, UUID templateId) {
        return templates.findByPublicIdAndOrganizationId(templateId.toString(), organizationId.toString())
                .switchIfEmpty(Mono.error(new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND",
                        "No existe la plantilla solicitada en esta organizacion")));
    }

    private String normalize(String value) {
        return value.trim().toUpperCase();
    }
}
