package com.medicalsys.notificaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.api.dto.MessageTemplateRequest;
import com.medicalsys.notificaciones.domain.MessageTemplate;
import com.medicalsys.notificaciones.domain.NotificationCategory;
import com.medicalsys.notificaciones.domain.NotificationChannel;
import com.medicalsys.notificaciones.domain.TemplateApprovalStatus;
import com.medicalsys.notificaciones.error.ApiException;
import com.medicalsys.notificaciones.repository.MessageTemplateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class MessageTemplateServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TEMPLATE_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 20, 0);

    @Mock MessageTemplateRepository repository;
    private MessageTemplateService service;

    @BeforeEach
    void setUp() {
        service = new MessageTemplateService(repository, JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-09-07T20:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsTemplateNormalizingCodeAndBlankOptionals() {
        when(repository.existsByOrganizationIdAndCodeAndLanguageCode(
                ORGANIZATION_ID.toString(), "APPOINTMENT_REMINDER", "es")).thenReturn(Mono.just(false));
        when(repository.save(any(MessageTemplate.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(service.create(ORGANIZATION_ID, request(" appointment_reminder ", "  ", " ")))
                .assertNext(response -> {
                    assertEquals("APPOINTMENT_REMINDER", response.code());
                    assertNull(response.providerTemplateName());
                    assertNull(response.subject());
                    assertEquals(List.of("patientName", "date"), response.variables());
                    assertEquals(NOW, response.createdAt());
                }).verifyComplete();
    }

    @Test
    void rejectsDuplicateCodeAndLanguageWithinOrganization() {
        when(repository.existsByOrganizationIdAndCodeAndLanguageCode(
                ORGANIZATION_ID.toString(), "APPOINTMENT_REMINDER", "es")).thenReturn(Mono.just(true));
        expectApiError(service.create(ORGANIZATION_ID, request("APPOINTMENT_REMINDER", null, null)),
                "TEMPLATE_ALREADY_EXISTS");
        verify(repository, never()).save(any());
    }

    @Test
    void listsTemplatesDecodingVariables() {
        when(repository.findAllByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID.toString()))
                .thenReturn(Flux.just(entity("[\"patientName\"]")));
        StepVerifier.create(service.list(ORGANIZATION_ID))
                .assertNext(response -> assertEquals(List.of("patientName"), response.variables()))
                .verifyComplete();
    }

    @Test
    void returnsEmptyVariablesWhenStoredValueIsNull() {
        mockFind(entity(null));
        StepVerifier.create(service.get(ORGANIZATION_ID, TEMPLATE_ID))
                .assertNext(response -> assertEquals(List.of(), response.variables()))
                .verifyComplete();
    }

    @Test
    void returnsTemplateByTenantAndPublicId() {
        mockFind(entity("[]"));
        StepVerifier.create(service.get(ORGANIZATION_ID, TEMPLATE_ID))
                .assertNext(response -> assertEquals(TEMPLATE_ID.toString(), response.id()))
                .verifyComplete();
    }

    @Test
    void getRejectsUnknownTemplate() {
        when(repository.findByPublicIdAndOrganizationId(TEMPLATE_ID.toString(), ORGANIZATION_ID.toString()))
                .thenReturn(Mono.empty());
        expectApiError(service.get(ORGANIZATION_ID, TEMPLATE_ID), "TEMPLATE_NOT_FOUND");
    }

    @Test
    void updatesTemplatePreservingIdentityAndCreationDate() {
        MessageTemplate current = entity("[]");
        mockFind(current);
        when(repository.save(any(MessageTemplate.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(service.update(ORGANIZATION_ID, TEMPLATE_ID,
                        request(" appointment_updated ", " meta_name ", " Subject ")))
                .assertNext(response -> {
                    assertEquals(TEMPLATE_ID.toString(), response.id());
                    assertEquals("APPOINTMENT_UPDATED", response.code());
                    assertEquals("meta_name", response.providerTemplateName());
                    assertEquals("Subject", response.subject());
                    assertEquals(NOW, response.updatedAt());
                }).verifyComplete();

        ArgumentCaptor<MessageTemplate> captor = ArgumentCaptor.forClass(MessageTemplate.class);
        verify(repository).save(captor.capture());
        assertEquals(current.createdAt(), captor.getValue().createdAt());
    }

    @Test
    void updateRejectsTemplateFromAnotherTenant() {
        when(repository.findByPublicIdAndOrganizationId(TEMPLATE_ID.toString(), ORGANIZATION_ID.toString()))
                .thenReturn(Mono.empty());
        expectApiError(service.update(ORGANIZATION_ID, TEMPLATE_ID,
                request("APPOINTMENT", null, null)), "TEMPLATE_NOT_FOUND");
    }

    @Test
    void reportsCorruptedVariablesStoredInDatabase() {
        mockFind(entity("broken-json"));
        StepVerifier.create(service.get(ORGANIZATION_ID, TEMPLATE_ID))
                .expectError(IllegalStateException.class).verify();
    }

    private MessageTemplateRequest request(String code, String providerName, String subject) {
        return new MessageTemplateRequest(code, NotificationChannel.WHATSAPP, NotificationCategory.UTILITY,
                providerName, "es", subject, "Hola {{patientName}}", List.of("patientName", "date"),
                TemplateApprovalStatus.APPROVED, true);
    }

    private MessageTemplate entity(String variables) {
        LocalDateTime created = NOW.minusDays(1);
        return new MessageTemplate(7L, TEMPLATE_ID.toString(), ORGANIZATION_ID.toString(), "APPOINTMENT_REMINDER",
                NotificationChannel.WHATSAPP, NotificationCategory.UTILITY, "appointment_reminder", "es",
                null, "Hola", variables, TemplateApprovalStatus.APPROVED, true, created, created);
    }

    private void mockFind(MessageTemplate value) {
        when(repository.findByPublicIdAndOrganizationId(TEMPLATE_ID.toString(), ORGANIZATION_ID.toString()))
                .thenReturn(Mono.just(value));
    }

    private void expectApiError(org.reactivestreams.Publisher<?> publisher, String code) {
        StepVerifier.create(publisher).expectErrorSatisfies(error -> {
            ApiException apiError = assertInstanceOf(ApiException.class, error);
            assertEquals(code, apiError.code());
        }).verify();
    }
}
