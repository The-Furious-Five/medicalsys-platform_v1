package com.medicalsys.marketing.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resultado de materializar la audiencia de una campana.
 *
 * <p>Devuelve los excluidos por opt-out ademas de los alcanzables porque esa cifra es la evidencia
 * de que la exclusion se evaluo, y es lo que se pregunta cuando alguien reclama haber recibido un
 * mensaje que no queria.
 *
 * @param campaignId    campana materializada
 * @param matched       pacientes que cumplen el criterio
 * @param reachable     destinatarios que si recibiran el mensaje
 * @param excludedOptOut destinatarios excluidos por opt-out, registrados con su motivo
 * @param truncated     si la audiencia se corto por el tope configurado
 * @param materializedAt instante de la materializacion
 */
public record MaterializeAudienceResponse(
        UUID campaignId,
        long matched,
        long reachable,
        long excludedOptOut,
        boolean truncated,
        OffsetDateTime materializedAt) {
}
