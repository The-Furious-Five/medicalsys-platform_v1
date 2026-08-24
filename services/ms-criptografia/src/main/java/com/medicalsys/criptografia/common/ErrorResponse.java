package com.medicalsys.criptografia.common;

import java.time.Instant;

/**
 * Cuerpo de error uniforme del equipo MedicalSys.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String correlationId,
        String path
) {}
