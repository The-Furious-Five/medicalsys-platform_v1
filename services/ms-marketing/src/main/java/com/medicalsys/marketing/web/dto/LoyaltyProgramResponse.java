package com.medicalsys.marketing.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Programa de fidelizacion.
 *
 * @param id                   UUID publico del programa
 * @param name                 nombre dentro de la organizacion
 * @param pointsPerBob         puntos por boliviano facturado
 * @param bobPerPoint          valor en bolivianos de cada punto
 * @param pointsValidityMonths meses de vigencia de los puntos
 * @param active               si sigue vigente
 * @param createdAt            instante de creacion
 */
public record LoyaltyProgramResponse(
        UUID id,
        String name,
        BigDecimal pointsPerBob,
        BigDecimal bobPerPoint,
        Integer pointsValidityMonths,
        boolean active,
        OffsetDateTime createdAt) {
}
