package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.CampaignChannel;
import com.medicalsys.marketing.domain.enums.CampaignType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Alta de una campana. Nace siempre en BORRADOR: nada sale hasta que se lance explicitamente.
 *
 * @param name              nombre visible de la campana
 * @param type              naturaleza: comercial o de salud preventiva
 * @param objective         objetivo declarado, para quien la audite despues
 * @param channel           canal por el que entregara ms-notificaciones
 * @param messageTemplateId plantilla de ms-notificaciones; referencia debil, sin FK
 * @param segmentId         segmento que define la audiencia
 * @param promotionId       cupon asociado, si la campana lo lleva
 * @param startsAt          inicio de la vigencia
 * @param endsAt            fin de la vigencia, opcional
 * @param budgetBob         presupuesto en bolivianos, opcional
 */
public record CreateCampaignRequest(
        @NotBlank(message = "no debe estar vacio")
        @Size(max = 160, message = "debe tener como maximo 160 caracteres")
        String name,

        @NotNull(message = "es obligatorio")
        CampaignType type,

        @Size(max = 300, message = "debe tener como maximo 300 caracteres")
        String objective,

        CampaignChannel channel,

        UUID messageTemplateId,

        UUID segmentId,

        UUID promotionId,

        @NotNull(message = "es obligatoria")
        OffsetDateTime startsAt,

        OffsetDateTime endsAt,

        @PositiveOrZero(message = "no puede ser negativo")
        BigDecimal budgetBob) {
}
