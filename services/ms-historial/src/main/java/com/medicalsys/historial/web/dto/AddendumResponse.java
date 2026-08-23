package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.AddendumReason;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Adicion inmutable sobre un encuentro firmado.
 *
 * @param id             identificador local
 * @param professionalId profesional que la firmo
 * @param reason         motivo
 * @param text           texto descifrado, sujeto a rol
 * @param contentHash    SHA-256 del texto
 * @param createdAt      instante del registro
 */
public record AddendumResponse(
        Long id,
        UUID professionalId,
        AddendumReason reason,
        String text,
        String contentHash,
        OffsetDateTime createdAt) {
}
