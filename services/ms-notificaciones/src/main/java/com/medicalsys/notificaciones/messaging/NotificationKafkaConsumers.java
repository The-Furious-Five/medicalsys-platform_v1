package com.medicalsys.notificaciones.messaging;

import com.medicalsys.notificaciones.api.dto.OrganizationReferenceRequest;
import com.medicalsys.notificaciones.api.dto.PatientReferenceRequest;
import com.medicalsys.notificaciones.api.dto.PreferenceReferenceRequest;
import com.medicalsys.notificaciones.service.CampaignNotificationService;
import com.medicalsys.notificaciones.service.DomainEventNotificationService;
import com.medicalsys.notificaciones.service.ReferenceSyncService;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "medicalsys.notifications", name = "kafka-enabled", havingValue = "true")
public class NotificationKafkaConsumers {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    // Una campana puede traer miles de destinatarios paginados; el listener de agenda/historial/
    // facturacion procesa un evento por paciente y le alcanza con TIMEOUT.
    private static final Duration CAMPAIGN_TIMEOUT = Duration.ofSeconds(120);

    private final ObjectMapper objectMapper;
    private final ReferenceSyncService references;
    private final DomainEventNotificationService events;
    private final CampaignNotificationService campaigns;

    public NotificationKafkaConsumers(ObjectMapper objectMapper, ReferenceSyncService references,
            DomainEventNotificationService events, CampaignNotificationService campaigns) {
        this.objectMapper = objectMapper;
        this.references = references;
        this.events = events;
        this.campaigns = campaigns;
    }

    @KafkaListener(topics = "tenencia.organizacion")
    public void organization(String payload) {
        references.upsertOrganization(read(payload, OrganizationReferenceRequest.class)).block(TIMEOUT);
    }

    @KafkaListener(topics = "pacientes.paciente")
    public void patient(String payload) {
        references.upsertPatient(read(payload, PatientReferenceRequest.class)).block(TIMEOUT);
    }

    @KafkaListener(topics = "pacientes.preferencia")
    public void preference(String payload) {
        references.upsertPreference(read(payload, PreferenceReferenceRequest.class)).block(TIMEOUT);
    }

    @KafkaListener(topics = {"agenda.cita", "historial.resultado", "facturacion.factura"})
    public void notificationEvent(String payload) {
        events.process(read(payload, NotificationDomainEvent.class)).then().block(TIMEOUT);
    }

    @KafkaListener(topics = "marketing.campana")
    public void campaignEvent(String payload) {
        campaigns.process(read(payload, CampaignLaunchedEvent.class)).then().block(CAMPAIGN_TIMEOUT);
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Evento Kafka incompatible con el contrato de notificaciones", exception);
        }
    }
}
