package com.medicalsys.notificaciones.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.api.dto.NotificationResponse;
import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationRule;
import com.medicalsys.notificaciones.domain.NotificationStatus;
import com.medicalsys.notificaciones.domain.TemplateApprovalStatus;
import com.medicalsys.notificaciones.messaging.NotificationDomainEvent;
import com.medicalsys.notificaciones.repository.MessageTemplateRepository;
import com.medicalsys.notificaciones.repository.NotificationRuleRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DomainEventNotificationServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    @Mock NotificationRuleRepository rules;
    @Mock MessageTemplateRepository templates;
    @Mock TemplateRenderer renderer;
    @Mock NotificationService notifications;
    private DomainEventNotificationService service;

    @BeforeEach
    void setUp() {
        service = new DomainEventNotificationService(rules, templates, renderer, notifications);
    }

    @Test
    void processesMatchingWhatsappRule() {
        NotificationDomainEvent event = event(" appointment_created ");
        NotificationRule rule = rule(1L, 7L);
        MessageTemplate template = template(7L, true);
        when(rules.findAllByOrganizationIdAndEventTypeAndChannelAndActiveTrue(
                ORGANIZATION_ID.toString(), "APPOINTMENT_CREATED", NotificationChannel.WHATSAPP))
                .thenReturn(Flux.just(rule));
        when(templates.findById(7L)).thenReturn(Mono.just(template));
        when(renderer.render(template, event.variables())).thenReturn("Hola Ana");
        when(notifications.createFromEvent(event, rule, template, "Hola Ana"))
                .thenReturn(Mono.just(response()));

        StepVerifier.create(service.process(event)).verifyComplete();
        verify(notifications).createFromEvent(event, rule, template, "Hola Ana");
    }

    @Test
    void ignoresInactiveTemplate() {
        NotificationDomainEvent event = event("APPOINTMENT_CREATED");
        when(rules.findAllByOrganizationIdAndEventTypeAndChannelAndActiveTrue(any(), any(), any()))
                .thenReturn(Flux.just(rule(1L, 7L)));
        when(templates.findById(7L)).thenReturn(Mono.just(template(7L, false)));

        StepVerifier.create(service.process(event)).verifyComplete();
        verify(renderer, never()).render(any(), any());
        verify(notifications, never()).createFromEvent(any(), any(), any(), any());
    }

    @Test
    void ignoresRuleWithDeletedTemplate() {
        when(rules.findAllByOrganizationIdAndEventTypeAndChannelAndActiveTrue(any(), any(), any()))
                .thenReturn(Flux.just(rule(1L, 7L)));
        when(templates.findById(7L)).thenReturn(Mono.empty());
        StepVerifier.create(service.process(event("EVENT"))).verifyComplete();
        verify(notifications, never()).createFromEvent(any(), any(), any(), any());
    }

    @Test
    void processesEveryMatchingRuleSequentially() {
        NotificationDomainEvent event = event("EVENT");
        NotificationRule first = rule(1L, 7L);
        NotificationRule second = rule(2L, 8L);
        MessageTemplate firstTemplate = template(7L, true);
        MessageTemplate secondTemplate = template(8L, true);
        when(rules.findAllByOrganizationIdAndEventTypeAndChannelAndActiveTrue(any(), eq("EVENT"), any()))
                .thenReturn(Flux.just(first, second));
        when(templates.findById(7L)).thenReturn(Mono.just(firstTemplate));
        when(templates.findById(8L)).thenReturn(Mono.just(secondTemplate));
        when(renderer.render(any(), any())).thenReturn("rendered");
        when(notifications.createFromEvent(eq(event), any(), any(), eq("rendered")))
                .thenReturn(Mono.just(response()));

        StepVerifier.create(service.process(event)).verifyComplete();
        verify(notifications, org.mockito.Mockito.times(2)).createFromEvent(eq(event), any(), any(), eq("rendered"));
    }

    @Test
    void propagatesTemplateRenderingFailure() {
        NotificationDomainEvent event = event("EVENT");
        MessageTemplate template = template(7L, true);
        when(rules.findAllByOrganizationIdAndEventTypeAndChannelAndActiveTrue(any(), any(), any()))
                .thenReturn(Flux.just(rule(1L, 7L)));
        when(templates.findById(7L)).thenReturn(Mono.just(template));
        when(renderer.render(template, event.variables())).thenThrow(new IllegalStateException("bad template"));

        StepVerifier.create(service.process(event)).expectErrorMessage("bad template").verify();
    }

    private NotificationDomainEvent event(String eventType) {
        return new NotificationDomainEvent(UUID.randomUUID(), ORGANIZATION_ID, UUID.randomUUID(), eventType,
                "APPOINTMENT", UUID.randomUUID(), "+59170000000", Map.of("patientName", "Ana"),
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));
    }

    private NotificationRule rule(long id, long templateId) {
        return new NotificationRule(id, "rule-" + id, ORGANIZATION_ID.toString(), "EVENT",
                NotificationChannel.WHATSAPP, templateId, 0, null, null, true,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private MessageTemplate template(long id, boolean active) {
        return new MessageTemplate(id, "template-" + id, ORGANIZATION_ID.toString(), "TEMPLATE",
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, "template", "es", null,
                "Hola", "[]", TemplateApprovalStatus.APPROVED, active, LocalDateTime.now(), LocalDateTime.now());
    }

    private NotificationResponse response() {
        return new NotificationResponse(UUID.randomUUID().toString(), null, ORGANIZATION_ID.toString(), null,
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, "EVENT", "APPOINTMENT", null,
                "+59170000000", "Hola", NotificationStatus.QUEUED, LocalDateTime.now(), 0, null,
                null, null, null, null, LocalDateTime.now(), LocalDateTime.now());
    }
}
