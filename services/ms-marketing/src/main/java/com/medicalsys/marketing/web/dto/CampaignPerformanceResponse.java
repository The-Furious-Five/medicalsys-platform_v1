package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.CampaignStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Rendimiento de una campana, leido de la vista {@code v_campaign_performance}.
 *
 * <p>Las tasas se calculan en la base y no aqui: la formula vive en un solo lugar y cualquier
 * consumidor -- este servicio, un informe, una consulta manual -- obtiene el mismo numero.
 *
 * @param campaignId        UUID publico de la campana
 * @param name              nombre visible
 * @param status            estado del ciclo de vida
 * @param totalRecipients   destinatarios materializados
 * @param totalSent         entregas informadas
 * @param totalRead         lecturas informadas
 * @param totalConverted    conversiones atribuidas
 * @param sendRatePct       enviados sobre destinatarios
 * @param readRatePct       leidos sobre enviados
 * @param conversionRatePct conversiones sobre enviados
 * @param excludedOptOut    excluidos por haber pedido no recibir publicidad
 */
public record CampaignPerformanceResponse(
        UUID campaignId,
        String name,
        CampaignStatus status,
        long totalRecipients,
        long totalSent,
        long totalRead,
        long totalConverted,
        BigDecimal sendRatePct,
        BigDecimal readRatePct,
        BigDecimal conversionRatePct,
        long excludedOptOut) {
}
