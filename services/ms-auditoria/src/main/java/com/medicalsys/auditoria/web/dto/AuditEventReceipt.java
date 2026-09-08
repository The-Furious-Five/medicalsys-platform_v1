package com.medicalsys.auditoria.web.dto;

import com.medicalsys.auditoria.domain.model.AuditEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Acuse que recibe el microservicio productor tras asentar un evento.
 *
 * <p>Devuelve la posicion y el hash y no el evento completo: el productor ya conoce lo que envio, y
 * el acuse minimo evita que un servicio se acostumbre a leer auditoria por la puerta de escritura.
 *
 * @param eventId        identificador publico definitivo del asiento
 * @param sequenceNumber posicion dentro de la cadena de la organizacion
 * @param recordedAt     instante en que la bitacora tomo el hecho
 * @param recordHash     hash del asiento; el productor puede conservarlo como comprobante
 */
public record AuditEventReceipt(UUID eventId, long sequenceNumber, Instant recordedAt, String recordHash) {

    public static AuditEventReceipt from(AuditEvent event) {
        return new AuditEventReceipt(event.getPublicId(), event.getSequenceNumber(),
                event.getRecordedAt(), event.getRecordHash());
    }
}
