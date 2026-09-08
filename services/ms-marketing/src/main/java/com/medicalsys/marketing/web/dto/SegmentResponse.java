package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.privacy.SegmentCriteria;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Segmento tal como lo devuelve el contrato.
 *
 * <p>{@code usesClinicalData} viaja en la respuesta aunque siempre valga {@code false}. Es
 * deliberado: quien audite el sistema puede comprobar la promesa desde la API, sin acceso a la base.
 *
 * @param id               UUID publico del segmento
 * @param name             nombre dentro de la organizacion
 * @param description      descripcion libre
 * @param criteria         criterio vigente, ya vinculado a tipos
 * @param usesClinicalData invariante de privacidad; siempre false
 * @param estimatedSize    tamano de la ultima evaluacion, o nulo si nunca se evaluo
 * @param lastEvaluatedAt  instante de la ultima evaluacion
 * @param active           si el segmento sigue disponible para nuevas campanas
 * @param createdAt        instante de creacion
 */
public record SegmentResponse(
        UUID id,
        String name,
        String description,
        SegmentCriteria criteria,
        boolean usesClinicalData,
        Long estimatedSize,
        OffsetDateTime lastEvaluatedAt,
        boolean active,
        OffsetDateTime createdAt) {
}
