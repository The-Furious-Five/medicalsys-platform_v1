package com.medicalsys.tenencia.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Prestacion facturable publicada en la ruta interna {@code GET /internal/v1/prestaciones/{id}}.
 *
 * <p>Este record es el contrato congelado que consume ms-facturacion: sus nombres de campo se
 * corresponden uno a uno con los de su cliente. Cambiar un nombre aqui rompe la emision de facturas
 * aunque ambos servicios compilen, asi que un cambio incompatible exige una ruta {@code /internal/v2}.
 *
 * <p>Es el snapshot que la factura congela: codigo, descripcion y precio. El precio se publica desde
 * su propietario y no llega en el cuerpo de quien factura.
 *
 * @param id             UUID publico de la prestacion en tenancy_db
 * @param code           codigo interno de la prestacion, por ejemplo CONSULTATION
 * @param name           descripcion que se imprime en el detalle
 * @param basePriceBob   precio unitario en bolivianos; DECIMAL, nunca coma flotante
 * @param taxProductCode codigo de producto/servicio homologado; puede faltar en el catalogo
 * @param taxUnitCode    unidad de medida del catalogo del SIN; 58 = unidad
 * @param active         indica si la prestacion sigue vigente
 */
public record BillableServiceResponse(
        UUID id,
        String code,
        String name,
        BigDecimal basePriceBob,
        String taxProductCode,
        Integer taxUnitCode,
        boolean active) {
}
