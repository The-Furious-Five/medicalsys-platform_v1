package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.AddendumReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Adicion inmutable sobre un encuentro ya firmado.
 *
 * @param professionalId profesional que firma la adicion
 * @param reason         motivo de la adicion
 * @param text           texto en claro; se persiste cifrado
 */
public record CreateAddendumRequest(
        @NotNull(message = "professionalId es obligatorio") UUID professionalId,
        @NotNull(message = "reason es obligatorio") AddendumReason reason,
        @NotBlank(message = "text es obligatorio") @Size(max = 4000) String text) {
}
