package com.medicalsys.catalogos.common;

import java.time.Instant;

/**
 * Cuerpo de error uniforme del equipo MedicalSys.
 * Todos los servicios usan el mismo contrato:
 * { timestamp, status, code, message, correlationId, path }
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String correlationId,
        String path
) {}
