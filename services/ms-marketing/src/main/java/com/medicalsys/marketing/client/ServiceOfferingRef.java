package com.medicalsys.marketing.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Prestacion tal como marketing la replica.
 *
 * <p>Se corresponde con el contrato congelado {@code GET /internal/v1/prestaciones/{id}} de
 * ms-tenencia, del que se conservan solo cuatro campos: identificador, nombre, precio y vigencia.
 * Los codigos tributarios que ese contrato tambien publica son asunto de ms-facturacion.
 *
 * @param id           UUID publico de la prestacion en tenancy_db
 * @param name         descripcion visible
 * @param basePriceBob precio unitario en bolivianos
 * @param active       si la prestacion sigue vigente
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceOfferingRef(UUID id, String name, BigDecimal basePriceBob, boolean active) {
}
