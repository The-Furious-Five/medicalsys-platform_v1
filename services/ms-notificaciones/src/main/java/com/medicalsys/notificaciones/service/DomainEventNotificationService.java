package com.medicalsys.notificaciones.service;

import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.messaging.NotificationDomainEvent;
import com.medicalsys.notificaciones.repository.MessageTemplateRepository;
import com.medicalsys.notificaciones.repository.NotificationRuleRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class DomainEventNotificationService {

    private final NotificationRuleRepository rules;
    private final MessageTemplateRepository templates;
    private final TemplateRenderer renderer;
    private final NotificationService notifications;

    public DomainEventNotificationService(NotificationRuleRepository rules, MessageTemplateRepository templates,
            TemplateRenderer renderer, NotificationService notifications) {
        this.rules = rules;
        this.templates = templates;
        this.renderer = renderer;
        this.notifications = notifications;
    }

    public Flux<Void> process(NotificationDomainEvent event) {
        String eventType = event.eventType().trim().toUpperCase();
        return rules.findAllByOrganizationIdAndEventTypeAndChannelAndActiveTrue(
                        event.organizationId().toString(), eventType, NotificationChannel.WHATSAPP)
                .concatMap(rule -> templates.findById(rule.templateId())
                        .filter(template -> template.active())
                        .flatMap(template -> notifications.createFromEvent(
                                event, rule, template, renderer.render(template, event.variables())))
                        .then());
    }
}
