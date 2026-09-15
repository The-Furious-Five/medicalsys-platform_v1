package com.medicalsys.notificaciones.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Espejo del evento {@code marketing.campana} publicado por ms-marketing (ver su
 * {@code docs/contracts.md}, seccion 4). No trae la audiencia: es un claim check, la lista de
 * destinatarios se pide por separado a {@code recipientsUri}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CampaignLaunchedEvent(
        UUID campaignId,
        UUID organizationId,
        String action,
        String name,
        String type,
        String channel,
        String status,
        UUID messageTemplateId,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        int totalRecipients,
        String recipientsUri,
        String couponCode,
        UUID promotionId) {
}
