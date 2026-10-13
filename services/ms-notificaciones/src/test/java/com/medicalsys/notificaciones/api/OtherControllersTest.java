package com.medicalsys.notificaciones.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.api.dto.MessageTemplateRequest;
import com.medicalsys.notificaciones.api.dto.MessageTemplateResponse;
import com.medicalsys.notificaciones.api.dto.NotificationRuleRequest;
import com.medicalsys.notificaciones.api.dto.NotificationRuleResponse;
import com.medicalsys.notificaciones.api.dto.OrganizationReferenceRequest;
import com.medicalsys.notificaciones.api.dto.PatientReferenceRequest;
import com.medicalsys.notificaciones.api.dto.PreferenceReferenceRequest;
import com.medicalsys.notificaciones.api.dto.SendWhatsappMessageRequest;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.TemplateApprovalStatus;
import com.medicalsys.notificaciones.service.MessageTemplateService;
import com.medicalsys.notificaciones.service.NotificationRuleService;
import com.medicalsys.notificaciones.service.ReferenceSyncService;
import com.medicalsys.notificaciones.service.WhatsappManualMessageService;
import com.medicalsys.notificaciones.service.WhatsappWebhookService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OtherControllersTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TEMPLATE_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID RULE_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    @Mock MessageTemplateService templateService;
    @Mock NotificationRuleService ruleService;
    @Mock ReferenceSyncService referenceService;
    @Mock WhatsappWebhookService webhookService;
    @Mock WhatsappManualMessageService manualService;

    @Test
    void templateControllerCreatesWithLocation() {
        MessageTemplateController controller = new MessageTemplateController(templateService);
        when(templateService.create(ORGANIZATION_ID, templateRequest())).thenReturn(Mono.just(templateResponse()));
        StepVerifier.create(controller.create(ORGANIZATION_ID.toString(), templateRequest()))
                .assertNext(response -> {
                    assertEquals(201, response.getStatusCode().value());
                    assertEquals("/api/v1/notifications/templates/" + TEMPLATE_ID,
                            response.getHeaders().getLocation().toString());
                }).verifyComplete();
    }

    @Test
    void templateControllerListsGetsAndUpdates() {
        MessageTemplateController controller = new MessageTemplateController(templateService);
        when(templateService.list(ORGANIZATION_ID)).thenReturn(Flux.just(templateResponse()));
        when(templateService.get(ORGANIZATION_ID, TEMPLATE_ID)).thenReturn(Mono.just(templateResponse()));
        when(templateService.update(ORGANIZATION_ID, TEMPLATE_ID, templateRequest()))
                .thenReturn(Mono.just(templateResponse()));
        StepVerifier.create(controller.list(ORGANIZATION_ID.toString())).expectNextCount(1).verifyComplete();
        StepVerifier.create(controller.get(ORGANIZATION_ID.toString(), TEMPLATE_ID)).expectNextCount(1).verifyComplete();
        StepVerifier.create(controller.update(ORGANIZATION_ID.toString(), TEMPLATE_ID, templateRequest()))
                .expectNextCount(1).verifyComplete();
    }

    @Test
    void ruleControllerCreatesWithLocation() {
        NotificationRuleController controller = new NotificationRuleController(ruleService);
        when(ruleService.create(ORGANIZATION_ID, ruleRequest())).thenReturn(Mono.just(ruleResponse()));
        StepVerifier.create(controller.create(ORGANIZATION_ID.toString(), ruleRequest()))
                .assertNext(response -> {
                    assertEquals(201, response.getStatusCode().value());
                    assertEquals("/api/v1/notifications/rules/" + RULE_ID,
                            response.getHeaders().getLocation().toString());
                }).verifyComplete();
    }

    @Test
    void ruleControllerListsAndUpdates() {
        NotificationRuleController controller = new NotificationRuleController(ruleService);
        when(ruleService.list(ORGANIZATION_ID)).thenReturn(Flux.just(ruleResponse()));
        when(ruleService.update(ORGANIZATION_ID, RULE_ID, ruleRequest())).thenReturn(Mono.just(ruleResponse()));
        StepVerifier.create(controller.list(ORGANIZATION_ID.toString())).expectNextCount(1).verifyComplete();
        StepVerifier.create(controller.update(ORGANIZATION_ID.toString(), RULE_ID, ruleRequest()))
                .expectNextCount(1).verifyComplete();
    }

    @Test
    void internalControllerUpsertsOrganization() {
        InternalReferenceController controller = new InternalReferenceController(referenceService);
        OrganizationReferenceRequest request = new OrganizationReferenceRequest(ORGANIZATION_ID, "Clinica", true);
        when(referenceService.upsertOrganization(request)).thenReturn(Mono.empty());
        StepVerifier.create(controller.organization(request))
                .assertNext(response -> assertEquals(204, response.getStatusCode().value())).verifyComplete();
    }

    @Test
    void internalControllerUpsertsPatient() {
        InternalReferenceController controller = new InternalReferenceController(referenceService);
        PatientReferenceRequest request = new PatientReferenceRequest(UUID.randomUUID(), ORGANIZATION_ID,
                "Ana", "+59170000000", true);
        when(referenceService.upsertPatient(request)).thenReturn(Mono.empty());
        StepVerifier.create(controller.patient(request))
                .assertNext(response -> assertEquals(204, response.getStatusCode().value())).verifyComplete();
    }

    @Test
    void internalControllerUpsertsPreference() {
        InternalReferenceController controller = new InternalReferenceController(referenceService);
        PreferenceReferenceRequest request = new PreferenceReferenceRequest(UUID.randomUUID(),
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, true, LocalDateTime.now(), null);
        when(referenceService.upsertPreference(request)).thenReturn(Mono.empty());
        StepVerifier.create(controller.preference(request))
                .assertNext(response -> assertEquals(204, response.getStatusCode().value())).verifyComplete();
    }

    @Test
    void webhookControllerDelegatesVerification() {
        WhatsappWebhookController controller = new WhatsappWebhookController(webhookService);
        when(webhookService.verify("subscribe", "token", "challenge")).thenReturn("challenge");
        assertEquals("challenge", controller.verify("subscribe", "token", "challenge"));
    }

    @Test
    void webhookControllerAcknowledgesAcceptedPayload() {
        WhatsappWebhookController controller = new WhatsappWebhookController(webhookService);
        when(webhookService.handle("{}", "signature")).thenReturn(Mono.empty());
        StepVerifier.create(controller.receive("signature", "{}"))
                .assertNext(response -> assertEquals(200, response.getStatusCode().value())).verifyComplete();
        verify(webhookService).handle("{}", "signature");
    }

    @Test
    void internalWhatsappSendDelegatesWithTheCallerOrganization() {
        // El OTP de consentimientos y de acceso a la ficha llega por /internal desde RBAC-04.
        InternalWhatsappController controller = new InternalWhatsappController(manualService);
        SendWhatsappMessageRequest request = new SendWhatsappMessageRequest("+59170000000", "Codigo 123456",
                null, null, List.of());
        when(manualService.send(ORGANIZATION_ID, request)).thenReturn(Mono.empty());
        StepVerifier.create(controller.send(ORGANIZATION_ID.toString(), request)).verifyComplete();
        verify(manualService).send(ORGANIZATION_ID, request);
    }

    private MessageTemplateRequest templateRequest() {
        return new MessageTemplateRequest("APPOINTMENT", NotificationChannel.WHATSAPP,
                NotificationCategory.UTILITY, "appointment", "es", null, "Hola", List.of(),
                TemplateApprovalStatus.APPROVED, true);
    }

    private MessageTemplateResponse templateResponse() {
        LocalDateTime now = LocalDateTime.now();
        return new MessageTemplateResponse(TEMPLATE_ID.toString(), "APPOINTMENT", NotificationChannel.WHATSAPP,
                NotificationCategory.UTILITY, "appointment", "es", null, "Hola", List.of(),
                TemplateApprovalStatus.APPROVED, true, now, now);
    }

    private NotificationRuleRequest ruleRequest() {
        return new NotificationRuleRequest("EVENT", NotificationChannel.WHATSAPP, TEMPLATE_ID, 0,
                null, null, true);
    }

    private NotificationRuleResponse ruleResponse() {
        LocalDateTime now = LocalDateTime.now();
        return new NotificationRuleResponse(RULE_ID.toString(), "EVENT", NotificationChannel.WHATSAPP,
                TEMPLATE_ID.toString(), 0, null, null, true, now, now);
    }
}
