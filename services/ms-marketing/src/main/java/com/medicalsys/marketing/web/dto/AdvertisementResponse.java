package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.AdvertisementPlacement;
import com.medicalsys.marketing.domain.enums.AdvertisementStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Anuncio tal como lo devuelve el contrato.
 *
 * @param id                UUID publico del anuncio
 * @param institutional     true si es de la plataforma y no de un tenant concreto
 * @param title             titulo visible
 * @param content           cuerpo del anuncio
 * @param imageDocumentId   imagen en ms-documental
 * @param targetUrl         destino del clic
 * @param serviceOfferingId prestacion asociada
 * @param placement         zona del producto
 * @param priority          1 es la mayor prioridad
 * @param targetAudience    filtro ligero de publico objetivo
 * @param validFrom         inicio de la vigencia
 * @param validUntil        fin de la vigencia
 * @param status            estado del ciclo de vida
 * @param impressions       veces que se mostro
 * @param clicks            veces que se pulso
 * @param createdAt         instante de creacion
 */
public record AdvertisementResponse(
        UUID id,
        boolean institutional,
        String title,
        String content,
        UUID imageDocumentId,
        String targetUrl,
        UUID serviceOfferingId,
        AdvertisementPlacement placement,
        int priority,
        Map<String, Object> targetAudience,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        AdvertisementStatus status,
        long impressions,
        long clicks,
        OffsetDateTime createdAt) {
}
