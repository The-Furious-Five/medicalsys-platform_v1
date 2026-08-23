package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.BackgroundType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Alta de antecedente.
 *
 * @param type      tipo de antecedente
 * @param title     resumen no sensible, usado para indexar
 * @param detail    narrativa en claro; se persiste cifrada y nunca se registra en logs
 * @param icd10Code codigo CIE-10 asociado, opcional
 * @param eventDate fecha del evento, opcional
 */
public record CreateMedicalBackgroundRequest(
        @NotNull(message = "type es obligatorio") BackgroundType type,
        @NotBlank(message = "title es obligatorio") @Size(max = 160) String title,
        @Size(max = 3000) String detail,
        @Size(max = 10) String icd10Code,
        LocalDate eventDate) {
}
