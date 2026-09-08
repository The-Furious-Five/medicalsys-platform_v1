package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.CampaignChannel;
import com.medicalsys.marketing.domain.enums.CampaignStatus;
import com.medicalsys.marketing.domain.enums.CampaignType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Campana con sus contadores.
 *
 * @param id                UUID publico de la campana
 * @param name              nombre visible
 * @param type              naturaleza de la campana
 * @param objective         objetivo declarado
 * @param channel           canal de entrega
 * @param messageTemplateId plantilla de ms-notificaciones
 * @param segmentId         segmento que define la audiencia
 * @param segmentName       nombre del segmento, para no exigir una segunda llamada
 * @param promotionId       cupon asociado
 * @param couponCode        codigo del cupon asociado
 * @param startsAt          inicio de la vigencia
 * @param endsAt            fin de la vigencia
 * @param budgetBob         presupuesto en bolivianos
 * @param status            estado del ciclo de vida
 * @param totalRecipients   destinatarios alcanzables materializados
 * @param totalSent         entregas informadas por ms-notificaciones
 * @param totalRead         lecturas informadas por ms-notificaciones
 * @param totalConverted    citas atribuidas a la campana
 * @param createdAt         instante de creacion
 * @param updatedAt         instante del ultimo cambio
 */
public record CampaignResponse(
        UUID id,
        String name,
        CampaignType type,
        String objective,
        CampaignChannel channel,
        UUID messageTemplateId,
        UUID segmentId,
        String segmentName,
        UUID promotionId,
        String couponCode,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        BigDecimal budgetBob,
        CampaignStatus status,
        long totalRecipients,
        long totalSent,
        long totalRead,
        long totalConverted,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
