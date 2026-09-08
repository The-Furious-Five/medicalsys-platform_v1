package com.medicalsys.notificaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.api.dto.NotificationRuleRequest;
import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.NotificationRule;
import com.medicalsys.notificaciones.domain.TemplateApprovalStatus;
import com.medicalsys.notificaciones.error.ApiException;
import com.medicalsys.notificaciones.repository.MessageTemplateRepository;
import com.medicalsys.notificaciones.repository.NotificationRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class NotificationRuleServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TEMPLATE_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID RULE_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 20, 0);

    @Mock NotificationRuleRepository rules;
    @Mock MessageTemplateRepository templates;
    private NotificationRuleService service;

    @BeforeEach
    void setUp() {
        service = new NotificationRuleService(rules, templates,
                Clock.fixed(Instant.parse("2026-09-07T20:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsRuleWithNormalizedEventAndTemplateInternalId() {
        mockTemplate();
        when(rules.save(any(NotificationRule.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(service.create(ORGANIZATION_ID, request(" appointment_created ", -1440)))
                .assertNext(response -> {
                    assertEquals("APPOINTMENT_CREATED", response.eventType());
                    assertEquals(TEMPLATE_ID.toString(), response.templateId());
                    assertEquals(-1440, response.offsetMinutes());
                    assertEquals(NOW, response.createdAt());
                }).verifyComplete();

        ArgumentCaptor<NotificationRule> captor = ArgumentCaptor.forClass(NotificationRule.class);
        verify(rules).save(captor.capture());
        assertEquals(7L, captor.getValue().templateId());
    }

    @Test
    void createRejectsTemplateOutsideTenant() {
        when(templates.findByPublicIdAndOrganizationId(TEMPLATE_ID.toString(), ORGANIZATION_ID.toString()))
                .thenReturn(Mono.empty());
        expectApiError(service.create(ORGANIZATION_ID, request("EVENT", 0)), "TEMPLATE_NOT_FOUND");
        verify(rules, never()).save(any());
    }

    @Test
    void listsRulesResolvingPublicTemplateIdentifiers() {
        when(rules.findAllByOrganizationIdOrderByEventTypeAsc(ORGANIZATION_ID.toString()))
                .thenReturn(Flux.just(rule()));
        when(templates.findById(7L)).thenReturn(Mono.just(template()));
        StepVerifier.create(service.list(ORGANIZATION_ID))
                .assertNext(response -> assertEquals(TEMPLATE_ID.toString(), response.templateId()))
                .verifyComplete();
    }

    @Test
    void listOmitsRulesWhoseTemplateNoLongerExists() {
        when(rules.findAllByOrganizationIdOrderByEventTypeAsc(ORGANIZATION_ID.toString()))
                .thenReturn(Flux.just(rule()));
        when(templates.findById(7L)).thenReturn(Mono.empty());
        StepVerifier.create(service.list(ORGANIZATION_ID)).verifyComplete();
    }

    @Test
    void updatesRuleAndPreservesItsIdentity() {
        when(rules.findByPublicIdAndOrganizationId(RULE_ID.toString(), ORGANIZATION_ID.toString()))
                .thenReturn(Mono.just(rule()));
        mockTemplate();
        when(rules.save(any(NotificationRule.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(service.update(ORGANIZATION_ID, RULE_ID, request(" invoice_issued ", 10)))
                .assertNext(response -> {
                    assertEquals(RULE_ID.toString(), response.id());
                    assertEquals("INVOICE_ISSUED", response.eventType());
                    assertEquals(10, response.offsetMinutes());
                    assertEquals(NOW, response.updatedAt());
                }).verifyComplete();
    }

    @Test
    void updateRejectsUnknownRule() {
        when(rules.findByPublicIdAndOrganizationId(RULE_ID.toString(), ORGANIZATION_ID.toString()))
                .thenReturn(Mono.empty());
        mockTemplate();
        expectApiError(service.update(ORGANIZATION_ID, RULE_ID, request("EVENT", 0)), "RULE_NOT_FOUND");
    }

    @Test
    void updateRejectsUnknownReplacementTemplate() {
        when(rules.findByPublicIdAndOrganizationId(RULE_ID.toString(), ORGANIZATION_ID.toString()))
                .thenReturn(Mono.just(rule()));
        when(templates.findByPublicIdAndOrganizationId(TEMPLATE_ID.toString(), ORGANIZATION_ID.toString()))
                .thenReturn(Mono.empty());
        expectApiError(service.update(ORGANIZATION_ID, RULE_ID, request("EVENT", 0)), "TEMPLATE_NOT_FOUND");
    }

    private NotificationRuleRequest request(String event, int offset) {
        return new NotificationRuleRequest(event, NotificationChannel.WHATSAPP, TEMPLATE_ID, offset,
                LocalTime.of(8, 0), LocalTime.of(20, 0), true);
    }

    private void mockTemplate() {
        when(templates.findByPublicIdAndOrganizationId(TEMPLATE_ID.toString(), ORGANIZATION_ID.toString()))
                .thenReturn(Mono.just(template()));
    }

    private MessageTemplate template() {
        return new MessageTemplate(7L, TEMPLATE_ID.toString(), ORGANIZATION_ID.toString(), "TEMPLATE",
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, "template", "es", null,
                "Hola", "[]", TemplateApprovalStatus.APPROVED, true, NOW, NOW);
    }

    private NotificationRule rule() {
        return new NotificationRule(8L, RULE_ID.toString(), ORGANIZATION_ID.toString(), "APPOINTMENT_CREATED",
                NotificationChannel.WHATSAPP, 7L, -60, null, null, true, NOW.minusDays(1), NOW.minusDays(1));
    }

    private void expectApiError(org.reactivestreams.Publisher<?> publisher, String code) {
        StepVerifier.create(publisher).expectErrorSatisfies(error -> {
            ApiException apiError = assertInstanceOf(ApiException.class, error);
            assertEquals(code, apiError.code());
        }).verify();
    }
}
