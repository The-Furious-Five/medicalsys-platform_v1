package com.medicalsys.notificaciones.messaging;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalsys.notificaciones.service.CampaignNotificationService;
import com.medicalsys.notificaciones.service.DomainEventNotificationService;
import com.medicalsys.notificaciones.service.ReferenceSyncService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumersTest {

    @Mock ReferenceSyncService references;
    @Mock DomainEventNotificationService events;
    @Mock CampaignNotificationService campaigns;
    private NotificationKafkaConsumers consumers;

    @BeforeEach
    void setUp() {
        consumers = new NotificationKafkaConsumers(JsonMapper.builder().build(), references, events, campaigns);
    }

    @Test
    void consumesOrganizationReplicaEvent() {
        when(references.upsertOrganization(any())).thenReturn(Mono.empty());
        consumers.organization("""
                {"organizationId":"10000000-0000-0000-0000-000000000001","legalName":"Clinica","active":true}
                """);
        verify(references).upsertOrganization(any());
    }

    @Test
    void consumesPatientReplicaEvent() {
        when(references.upsertPatient(any())).thenReturn(Mono.empty());
        consumers.patient("""
                {"patientId":"20000000-0000-0000-0000-000000000001",
                 "organizationId":"10000000-0000-0000-0000-000000000001",
                 "displayName":"Ana","whatsappNumber":"+59170000000","active":true}
                """);
        verify(references).upsertPatient(any());
    }

    @Test
    void consumesPreferenceReplicaEvent() {
        when(references.upsertPreference(any())).thenReturn(Mono.empty());
        consumers.preference("""
                {"patientId":"20000000-0000-0000-0000-000000000001","channel":"WHATSAPP",
                 "category":"UTILITY","optedIn":true,"optedInAt":"2026-09-07T20:00:00"}
                """);
        verify(references).upsertPreference(any());
    }

    @Test
    void consumesDomainEventAndPreservesIdentifiers() {
        when(events.process(any())).thenReturn(Flux.empty());
        consumers.notificationEvent("""
                {"eventId":"30000000-0000-0000-0000-000000000001",
                 "organizationId":"10000000-0000-0000-0000-000000000001",
                 "patientId":"20000000-0000-0000-0000-000000000001",
                 "eventType":"APPOINTMENT_CREATED","entityType":"APPOINTMENT",
                 "destination":"+59170000000","variables":{},
                 "occurredAt":"2026-09-07T20:00:00"}
                """);
        ArgumentCaptor<NotificationDomainEvent> captor = ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(events).process(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                UUID.fromString("30000000-0000-0000-0000-000000000001"), captor.getValue().eventId());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 9, 7, 20, 0),
                captor.getValue().occurredAt());
    }

    @Test
    void rejectsKafkaPayloadThatViolatesJsonContract() {
        assertThrows(IllegalArgumentException.class, () -> consumers.notificationEvent("not-json"));
    }

    @Test
    void consumesCampaignEvent() {
        when(campaigns.process(any())).thenReturn(Flux.empty());
        consumers.campaignEvent("""
                {"campaignId":"40000000-0000-0000-0000-000000000001",
                 "organizationId":"10000000-0000-0000-0000-000000000001",
                 "action":"LANZADA","name":"Chequeo preventivo","channel":"WHATSAPP",
                 "totalRecipients":1,
                 "recipientsUri":"/internal/v1/campanas/40000000-0000-0000-0000-000000000001/destinatarios"}
                """);
        ArgumentCaptor<CampaignLaunchedEvent> captor = ArgumentCaptor.forClass(CampaignLaunchedEvent.class);
        verify(campaigns).process(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                UUID.fromString("40000000-0000-0000-0000-000000000001"), captor.getValue().campaignId());
        org.junit.jupiter.api.Assertions.assertEquals("LANZADA", captor.getValue().action());
    }
}
