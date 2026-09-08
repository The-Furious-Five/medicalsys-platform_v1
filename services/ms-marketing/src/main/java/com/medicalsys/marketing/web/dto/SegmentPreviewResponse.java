package com.medicalsys.marketing.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Vista previa de la audiencia de un criterio, sin crear ninguna campana.
 *
 * <p>Sirve para que quien administra vea el tamano antes de comprometerse: una campana lanzada sobre
 * un criterio equivocado ya no se puede recoger.
 *
 * @param matched     pacientes que cumplen el criterio
 * @param reachable   de esos, cuantos aceptan comunicaciones comerciales
 * @param optedOut    de esos, cuantos pidieron no recibirlas
 * @param sample      primeros miembros, para inspeccion visual
 * @param evaluatedAt instante de la evaluacion
 */
public record SegmentPreviewResponse(
        long matched,
        long reachable,
        long optedOut,
        List<AudienceMemberResponse> sample,
        OffsetDateTime evaluatedAt) {
}
