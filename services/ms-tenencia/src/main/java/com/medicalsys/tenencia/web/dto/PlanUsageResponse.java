package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.UsageMetric;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cuota consumida frente al limite del plan.
 *
 * @param limit     limite vigente; null = ilimitado
 * @param remaining unidades disponibles; null si el plan no impone limite
 * @param exceeded  indica si el consumo ya supera el limite
 */
public record PlanUsageResponse(
        UUID organizationId,
        UsageMetric metric,
        long currentValue,
        Long limit,
        Long remaining,
        boolean exceeded,
        LocalDate periodStart,
        LocalDate periodEnd,
        OffsetDateTime updatedAt) {
}
