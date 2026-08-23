package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.AdministrationRoute;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Detalle de una receta.
 *
 * <p>Debe traer {@code medicationCode} o {@code freeTextDescription}: una formula magistral no
 * tiene codigo, pero el producto nunca queda sin identificar.
 *
 * @param medicationCode      codigo del vademecum, validado contra ms-catalogos
 * @param freeTextDescription descripcion libre para preparados magistrales
 * @param dose                dosis, por ejemplo "500 mg"
 * @param administrationRoute via de administracion; por defecto ORAL
 * @param frequencyHours      intervalo entre tomas en horas
 * @param durationDays        duracion del tratamiento en dias
 * @param quantity            cantidad a dispensar; debe ser mayor que cero
 * @param unit                unidad de la cantidad
 * @param instructions        indicaciones en claro; se persisten cifradas
 */
public record PrescriptionItemRequest(
        @Size(max = 30) String medicationCode,
        @Size(max = 200) String freeTextDescription,
        @NotBlank(message = "dose es obligatorio") @Size(max = 80) String dose,
        AdministrationRoute administrationRoute,
        Integer frequencyHours,
        Integer durationDays,
        @DecimalMin(value = "0.01", message = "quantity debe ser mayor que cero") BigDecimal quantity,
        @Size(max = 20) String unit,
        @Size(max = 1000) String instructions) {
}
