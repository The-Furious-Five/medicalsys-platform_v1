package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.BackgroundType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Antecedente del expediente.
 *
 * <p>{@code detail} solo viaja descifrado hacia roles clinicos; en otros casos llega nulo.
 *
 * @param id           identificador local
 * @param type         tipo de antecedente
 * @param title        resumen no sensible
 * @param detail       narrativa descifrada, sujeta a rol
 * @param icd10Code    codigo CIE-10 asociado
 * @param eventDate    fecha del evento
 * @param active       vigencia
 * @param recordedById profesional que lo registro
 * @param recordedAt   instante del registro
 */
public record MedicalBackgroundResponse(
        Long id,
        BackgroundType type,
        String title,
        String detail,
        String icd10Code,
        LocalDate eventDate,
        boolean active,
        UUID recordedById,
        OffsetDateTime recordedAt) {
}
