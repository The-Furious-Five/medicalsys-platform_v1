package com.medicalsys.auditoria.common.error;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Formato de error comun acordado para el MVP (guia Sprint 2, seccion 4.4).
 *
 * @param timestamp     instante del fallo en ISO-8601 con zona
 * @param status        codigo HTTP devuelto
 * @param code          codigo estable de negocio
 * @param message       mensaje legible, sin detalles internos
 * @param correlationId identificador de la solicitud para reconstruir el salto
 * @param path          ruta solicitada
 * @param details       violaciones de validacion, cuando corresponde
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String correlationId,
        String path,
        List<FieldViolation> details) {

    /** Violacion puntual de una regla de validacion de entrada. */
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String correlationId, String path,
            List<FieldViolation> details) {
        return new ApiError(OffsetDateTime.now(ZoneOffset.UTC), code.status().value(), code.name(), message,
                correlationId, path, details == null || details.isEmpty() ? null : List.copyOf(details));
    }
}
