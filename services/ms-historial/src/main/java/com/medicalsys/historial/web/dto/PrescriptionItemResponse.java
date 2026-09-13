package com.medicalsys.historial.web.dto;

import com.medicalsys.historial.domain.enums.AdministrationRoute;
import java.math.BigDecimal;

/**
 * Detalle de la receta.
 *
 * @param itemNumber          numero correlativo dentro de la receta
 * @param medicationCode      codigo del vademecum
 * @param freeTextDescription descripcion libre
 * @param dose                dosis
 * @param administrationRoute via de administracion
 * @param frequencyHours      intervalo entre tomas en horas
 * @param durationDays        duracion en dias
 * @param quantity            cantidad a dispensar
 * @param unit                unidad de la cantidad
 * @param instructions        indicaciones descifradas, sujetas a rol
 */
public record PrescriptionItemResponse(
        Integer itemNumber,
        String medicationCode,
        String freeTextDescription,
        String dose,
        AdministrationRoute administrationRoute,
        Integer frequencyHours,
        Integer durationDays,
        BigDecimal quantity,
        String unit,
        String instructions) {
}
