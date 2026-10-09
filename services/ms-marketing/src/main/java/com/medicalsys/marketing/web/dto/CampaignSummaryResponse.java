package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.CampaignStatus;
import com.medicalsys.marketing.domain.enums.CampaignType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Proyeccion breve para el listado.
 *
 * @param id              UUID publico de la campana
 * @param name            nombre visible
 * @param type            naturaleza de la campana
 * @param status          estado del ciclo de vida
 * @param startsAt        inicio de la vigencia
 * @param totalRecipients destinatarios materializados
 * @param totalConverted  conversiones atribuidas
 */
public record CampaignSummaryResponse(
        UUID id,
        String name,
        CampaignType type,
        CampaignStatus status,
        OffsetDateTime startsAt,
        long totalRecipients,
        long totalConverted) {
}
