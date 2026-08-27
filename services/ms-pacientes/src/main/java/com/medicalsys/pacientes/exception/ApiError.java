package com.medicalsys.pacientes.exception;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Respuesta de error estable definida por el contrato HTTP de ms-pacientes. */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String correlationId,
        String path,
        List<FieldViolation> details) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String correlationId, String path,
            List<FieldViolation> details) {
        return new ApiError(
                OffsetDateTime.now(ZoneOffset.UTC),
                code.status().value(),
                code.name(),
                message,
                correlationId,
                path,
                details == null || details.isEmpty() ? null : List.copyOf(details));
    }
}
