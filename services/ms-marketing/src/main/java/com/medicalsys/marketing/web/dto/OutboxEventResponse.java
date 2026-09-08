package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.OutboxStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento saliente del outbox.
 *
 * <p>Se expone por la ruta interna para que un relay externo pueda drenarlo mientras la plataforma
 * no tenga broker, y para que la demostracion pueda ensenar que lanzar una campana produjo de verdad
 * un {@code marketing.campana}.
 *
 * @param eventId     identificador del evento
 * @param eventType   tipo publicado
 * @param aggregateId agregado que lo produjo
 * @param partitionKey clave de particion, que garantiza el orden por agregado
 * @param payload     cuerpo serializado
 * @param status      estado de publicacion
 * @param attempts    intentos realizados
 * @param createdAt   instante en que se encolo
 * @param publishedAt instante en que se publico
 */
public record OutboxEventResponse(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        String partitionKey,
        String payload,
        OutboxStatus status,
        int attempts,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt) {
}
