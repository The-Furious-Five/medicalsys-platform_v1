package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cupon tal como lo devuelve el contrato.
 *
 * @param id                    UUID publico del cupon
 * @param couponCode            codigo normalizado a mayusculas
 * @param name                  nombre visible
 * @param description           descripcion libre
 * @param discountType          forma del descuento
 * @param value                 porcentaje o monto, segun el tipo
 * @param serviceOfferingId     prestacion a la que aplica; nulo para cualquiera
 * @param maxRedemptions        cupo global; nulo para ilimitado
 * @param redemptionsPerPatient cuantas veces puede usarlo el mismo paciente
 * @param currentRedemptions    canjes ya consumidos
 * @param validFrom             inicio de la vigencia
 * @param validUntil            fin de la vigencia, inclusive
 * @param active                si sigue disponible
 * @param createdAt             instante de creacion
 */
public record PromotionResponse(
        UUID id,
        String couponCode,
        String name,
        String description,
        DiscountType discountType,
        BigDecimal value,
        UUID serviceOfferingId,
        Long maxRedemptions,
        int redemptionsPerPatient,
        long currentRedemptions,
        LocalDate validFrom,
        LocalDate validUntil,
        boolean active,
        OffsetDateTime createdAt) {
}
