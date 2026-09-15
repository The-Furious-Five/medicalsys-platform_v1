package com.medicalsys.notificaciones.service;

import com.medicalsys.notificaciones.integration.MarketingCampaignClient;
import com.medicalsys.notificaciones.integration.MarketingCampaignClient.Recipient;
import com.medicalsys.notificaciones.messaging.CampaignLaunchedEvent;
import com.medicalsys.notificaciones.messaging.NotificationDomainEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Traduce {@code marketing.campana} (un evento por campana, sin audiencia) en un
 * {@link NotificationDomainEvent} por destinatario, y lo entrega al mismo pipeline generico que ya
 * usan agenda/historial/facturacion.
 *
 * <p>Requiere que la organizacion tenga configurada una {@code notification_rule} activa para el
 * evento {@code MARKETING_CAMPAIGN} en WHATSAPP (mismo mecanismo que cualquier otro evento de
 * dominio) — sin eso, la campana se procesa pero no produce notificaciones, igual que pasaria con
 * un evento de agenda sin regla.
 */
@Service
public class CampaignNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CampaignNotificationService.class);
    private static final String EVENT_TYPE = "MARKETING_CAMPAIGN";
    private static final String ENTITY_TYPE = "CAMPAIGN";
    private static final String LAUNCH_ACTION = "LANZADA";

    private final MarketingCampaignClient marketing;
    private final ReferenceSyncService references;
    private final DomainEventNotificationService events;
    private final Clock clock = Clock.systemUTC();

    public CampaignNotificationService(MarketingCampaignClient marketing, ReferenceSyncService references,
            DomainEventNotificationService events) {
        this.marketing = marketing;
        this.references = references;
        this.events = events;
    }

    public Flux<Void> process(CampaignLaunchedEvent campaign) {
        if (!LAUNCH_ACTION.equalsIgnoreCase(campaign.action())) {
            return Flux.empty();
        }
        if (campaign.channel() != null && !"WHATSAPP".equalsIgnoreCase(campaign.channel())) {
            log.info("Campana {} en canal {} sin soporte de envio; se ignora", campaign.campaignId(),
                    campaign.channel());
            return Flux.empty();
        }
        return marketing.fetchRecipients(campaign.organizationId(), campaign.campaignId())
                .concatMap(recipient -> phoneOf(campaign, recipient)
                        .flatMapMany(phone -> events.process(domainEvent(campaign, recipient.patientId(), phone))));
    }

    private Mono<String> phoneOf(CampaignLaunchedEvent campaign, Recipient recipient) {
        return references.findWhatsappNumber(recipient.patientId().toString())
                .switchIfEmpty(Mono.<String>empty().doOnSubscribe(ignored -> log.info(
                        "Paciente {} de la campana {} sin telefono replicado; no se notifica",
                        recipient.patientId(), campaign.campaignId())));
    }

    private NotificationDomainEvent domainEvent(CampaignLaunchedEvent campaign, UUID patientId, String destination) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("campaignName", campaign.name());
        variables.put("couponCode", campaign.couponCode());
        return new NotificationDomainEvent(UUID.randomUUID(), campaign.organizationId(), patientId, EVENT_TYPE,
                ENTITY_TYPE, campaign.campaignId(), destination, variables, LocalDateTime.now(clock), null);
    }
}
