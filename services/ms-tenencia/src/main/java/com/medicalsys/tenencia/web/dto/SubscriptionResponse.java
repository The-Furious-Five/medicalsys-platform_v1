package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.BillingCycle;
import com.medicalsys.tenencia.domain.enums.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Contrato entre el tenant y su plan.
 *
 * @param effective indica si el contrato habilita el uso del producto en la fecha de consulta
 */
public record SubscriptionResponse(
        Long id,
        UUID organizationId,
        SubscriptionPlanResponse plan,
        BillingCycle billingCycle,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal agreedPriceBob,
        SubscriptionStatus status,
        boolean effective,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
