package com.medicalsys.facturacion.client;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Prestacion facturable publicada por ms-tenencia.
 *
 * <p>Es el snapshot que la factura congela: codigo, descripcion y precio. Si manana cambia la tarifa
 * del catalogo, la factura ya emitida no cambia.
 *
 * @param id              UUID publico de la prestacion en tenancy_db
 * @param code            codigo interno de la prestacion, por ejemplo CONSULTATION
 * @param name            descripcion que se imprime en el detalle
 * @param basePriceBob    precio unitario en bolivianos; DECIMAL, nunca coma flotante
 * @param taxProductCode  codigo de producto/servicio homologado; puede faltar en el catalogo
 * @param taxUnitCode     unidad de medida del catalogo del SIN; 58 = unidad
 * @param active          indica si la prestacion sigue vigente
 */
public record BillableService(
        UUID id,
        String code,
        String name,
        BigDecimal basePriceBob,
        String taxProductCode,
        Integer taxUnitCode,
        boolean active) {

    public BillableService {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(basePriceBob, "basePriceBob");
    }
}
