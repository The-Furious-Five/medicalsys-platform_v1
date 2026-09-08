package com.medicalsys.marketing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Alta de un segmento de audiencia.
 *
 * <p>{@code criteria} llega como mapa sin tipar a proposito. Se valida primero contra la lista
 * blanca de {@code SegmentCriteriaField} y solo despues se vincula al tipo: si se vinculara antes,
 * Jackson descartaria en silencio un campo desconocido y un intento de segmentar por diagnostico
 * pasaria inadvertido en vez de terminar en un 422 registrado.
 *
 * <p>El cuerpo no acepta {@code organizationId}: lo impone el gateway desde el JWT.
 *
 * @param name        nombre del segmento, unico dentro de la organizacion
 * @param description descripcion libre para quien lo administre
 * @param criteria    criterio declarativo; solo ejes administrativos y demograficos
 */
public record CreateSegmentRequest(
        @NotBlank(message = "no debe estar vacio")
        @Size(max = 120, message = "debe tener como maximo 120 caracteres")
        String name,

        @Size(max = 300, message = "debe tener como maximo 300 caracteres")
        String description,

        @NotEmpty(message = "debe declarar al menos un criterio")
        Map<String, Object> criteria) {
}
