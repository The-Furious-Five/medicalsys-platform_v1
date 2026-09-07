package com.medicalsys.auditoria.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de recalcular la cadena de hashes de un tramo de la bitacora.
 *
 * <p>Que la bitacora sea append-only en el codigo no impide que alguien con acceso al motor edite
 * una fila. El encadenamiento si: cambiar un evento invalida su hash y el de todos los siguientes.
 * Esta verificacion recalcula el tramo y senala el primer punto donde la cadena deja de cerrar.
 *
 * @param verifiedEvents eventos recalculados en esta pasada
 * @param intact         true si todo el tramo cierra
 * @param brokenAtSequenceNumber primera posicion inconsistente; null si el tramo esta intacto
 * @param brokenEventId  evento correspondiente a esa posicion; null si el tramo esta intacto
 * @param detail         que fallo exactamente, en lenguaje del contrato y sin datos internos
 */
public record AuditIntegrityResponse(
        long fromSequenceNumber,
        long toSequenceNumber,
        long verifiedEvents,
        boolean intact,
        Long brokenAtSequenceNumber,
        UUID brokenEventId,
        String detail,
        Instant verifiedAt) {
}
