package com.medicalsys.marketing.event;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.medicalsys.marketing.common.DateTimes;
import com.medicalsys.marketing.common.correlation.CorrelationId;
import com.medicalsys.marketing.common.error.ApiException;
import com.medicalsys.marketing.common.error.ErrorCode;
import com.medicalsys.marketing.domain.model.Campaign;
import com.medicalsys.marketing.domain.model.OutboxEvent;
import com.medicalsys.marketing.repository.OutboxEventRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica {@code marketing.campana}, el unico evento que ms-marketing emite.
 *
 * <p>El evento se escribe en el outbox dentro de la <b>misma transaccion</b> que el cambio de estado
 * de la campana. Es lo que evita el caso feo: una campana que quedo EN_EJECUCION en la base y de la
 * que ms-notificaciones nunca se entero porque el proceso murio entre las dos escrituras.
 *
 * <p><b>El evento no lleva la audiencia.</b> Lleva el hecho y el tamano; los destinatarios se piden
 * despues por {@code GET /internal/v1/campanas/&#123;id&#125;/destinatarios}. Meter 5.000 telefonos en
 * un mensaje seria un cuerpo enorme, dificil de reintentar y, sobre todo, publicaria datos de
 * contacto en un canal que otros servicios pueden leer. Quien necesita la lista es exactamente uno,
 * y la pide.
 *
 * <p>Notese que ni el evento ni la lista de destinatarios pueden contener un dato clinico: el
 * cuerpo se arma desde la campana y desde {@code ref_patient}, y ninguno de los dos lo tiene.
 */
@Service
public class MarketingEventPublisher {

    /** Tipo del evento, tal como lo declara la seccion 5.12: "Publica marketing.campana". */
    public static final String CAMPAIGN_EVENT_TYPE = "marketing.campana";

    /** Tipo de agregado del outbox. */
    private static final String AGGREGATE_TYPE = "campaign";

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public MarketingEventPublisher(OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Encola {@code marketing.campana} con el cambio de estado indicado.
     *
     * @param campaign campana afectada
     * @param action   que le paso: {@code LANZADA}, {@code PAUSADA}, {@code FINALIZADA} o
     *                 {@code CANCELADA}. Va dentro del cuerpo y no en el tipo del evento para que el
     *                 consumidor se suscriba a un unico topico, como declara el contrato
     */
    @Transactional
    public OutboxEvent publishCampaign(Campaign campaign, String action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("campaignId", campaign.getPublicId());
        payload.put("organizationId", campaign.getOrganizationPublicId());
        payload.put("action", action);
        payload.put("name", campaign.getName());
        payload.put("type", campaign.getType());
        payload.put("channel", campaign.getChannel());
        payload.put("status", campaign.getStatus());
        payload.put("messageTemplateId", campaign.getMessageTemplatePublicId());
        payload.put("startsAt", DateTimes.toOffset(campaign.getStartsAt()));
        payload.put("endsAt", DateTimes.toOffset(campaign.getEndsAt()));
        payload.put("totalRecipients", campaign.getTotalRecipients());
        // Claim check: el consumidor pide la audiencia por esta ruta en vez de recibirla en el cuerpo.
        payload.put("recipientsUri", "/internal/v1/campanas/" + campaign.getPublicId() + "/destinatarios");
        if (campaign.getPromotion() != null) {
            payload.put("couponCode", campaign.getPromotion().getCouponCode());
            payload.put("promotionId", campaign.getPromotion().getPublicId());
        }

        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("correlationId", CorrelationId.current());
        headers.put("producer", "ms-marketing");
        headers.put("schemaVersion", 1);

        return outbox.save(OutboxEvent.pending(
                AGGREGATE_TYPE,
                campaign.getPublicId(),
                // Particion por tenant: garantiza el orden de las campanas de una misma clinica.
                campaign.getOrganizationPublicId().toString(),
                CAMPAIGN_EVENT_TYPE,
                serialize(payload),
                serialize(headers)));
    }

    private String serialize(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            // Un cuerpo que no serializa es un fallo de programacion, no de entrada: no hay dato del
            // usuario en el payload que pueda provocarlo.
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "No se pudo serializar el evento de campana", e);
        }
    }
}
