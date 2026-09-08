package com.medicalsys.marketing.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Canje registrado.
 *
 * @param redemptionId      UUID publico del canje
 * @param promotionId       cupon consumido
 * @param couponCode        codigo del cupon
 * @param patientId         paciente beneficiado
 * @param invoiceId         factura que lo consumio
 * @param discountAmountBob descuento aplicado
 * @param netAmountBob      importe resultante tras el descuento
 * @param redeemedAt        instante del canje
 * @param alreadyRedeemed   true si la peticion era un reintento y se devolvio el canje existente
 */
public record CouponRedemptionResponse(
        UUID redemptionId,
        UUID promotionId,
        String couponCode,
        UUID patientId,
        UUID invoiceId,
        BigDecimal discountAmountBob,
        BigDecimal netAmountBob,
        OffsetDateTime redeemedAt,
        boolean alreadyRedeemed) {
}
