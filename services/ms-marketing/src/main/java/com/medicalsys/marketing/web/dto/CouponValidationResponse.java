package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.DiscountType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado de validar un cupon.
 *
 * <p>Un cupon invalido responde 200 con {@code valid=false} y su motivo, no un 4xx. La distincion
 * importa: para ms-facturacion, "este cupon no sirve" es una respuesta esperada del flujo de cobro,
 * no un fallo de la peticion, y confundirlas obligaria a la caja a interpretar codigos de error para
 * decidir si puede seguir cobrando.
 *
 * @param valid          si el cupon puede aplicarse
 * @param reason         motivo del rechazo cuando no es valido
 * @param promotionId    UUID publico del cupon, cuando existe
 * @param couponCode     codigo consultado, normalizado
 * @param discountType   forma del descuento
 * @param discountAmountBob descuento calculado sobre la base recibida
 * @param netAmountBob   importe resultante tras el descuento
 */
public record CouponValidationResponse(
        boolean valid,
        String reason,
        UUID promotionId,
        String couponCode,
        DiscountType discountType,
        BigDecimal discountAmountBob,
        BigDecimal netAmountBob) {
}
