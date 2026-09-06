package com.medicalsys.marketing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Evento entrante de otro microservicio.
 *
 * <p>El MVP de la plataforma no tiene broker, asi que los eventos entran por esta ruta interna con
 * la misma forma que tendrian en un mensaje: identificador propio, tipo, instante y cuerpo. El dia
 * que aparezca el broker, cambia el transporte y no el procesamiento.
 *
 * <p>{@code eventId} es la clave del inbox de idempotencia. Reenviar el mismo evento no vuelve a
 * aplicarlo, que es lo que convierte un transporte at-least-once en un efecto effectively-once.
 *
 * @param eventId        identificador unico del evento en el productor
 * @param eventType      tipo, por ejemplo {@code facturacion.factura.emitida}
 * @param occurredAt     instante en que ocurrio en el productor
 * @param organizationId tenant al que pertenece el hecho
 * @param payload        cuerpo del evento; se inspecciona antes de proyectarlo
 */
public record IncomingEventRequest(
        @NotNull(message = "es obligatorio")
        UUID eventId,

        @NotBlank(message = "no debe estar vacio")
        @Size(max = 80, message = "debe tener como maximo 80 caracteres")
        String eventType,

        OffsetDateTime occurredAt,

        @NotNull(message = "es obligatorio")
        UUID organizationId,

        @NotNull(message = "es obligatorio")
        Map<String, Object> payload) {
}
