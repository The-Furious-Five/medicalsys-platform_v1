package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.BillingCycle;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contratacion de un plan para el tenant.
 *
 * @param planCode       codigo del plan a contratar
 * @param billingCycle   periodicidad del cobro; MONTHLY si falta
 * @param startDate      inicio de vigencia; hoy si falta
 * @param endDate        fin de vigencia; null = vigente indefinidamente
 * @param agreedPriceBob precio pactado; el de lista del plan si falta
 */
public record CreateSubscriptionRequest(
        @NotBlank @Size(max = 30) String planCode,
        BillingCycle billingCycle,
        LocalDate startDate,
        LocalDate endDate,
        @DecimalMin(value = "0.00", message = "no puede ser negativo")
                @Digits(integer = 8, fraction = 2) BigDecimal agreedPriceBob) {
}
