package com.medicalsys.marketing.web.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Modificacion de un segmento.
 *
 * <p>El nombre no se cambia: es la clave por la que la organizacion lo reconoce y por la que se
 * declara la restriccion de unicidad. Cambiar el criterio invalida el tamano estimado, que se
 * recalcula en la siguiente evaluacion.
 *
 * @param description nueva descripcion, o nulo para conservarla
 * @param criteria    nuevo criterio, o nulo para conservarlo
 */
public record UpdateSegmentRequest(
        @Size(max = 300, message = "debe tener como maximo 300 caracteres")
        String description,

        Map<String, Object> criteria) {
}
