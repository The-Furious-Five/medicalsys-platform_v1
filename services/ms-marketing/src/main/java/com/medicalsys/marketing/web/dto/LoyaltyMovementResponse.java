package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.LoyaltyMovementType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Asiento del libro mayor de puntos.
 *
 * @param id               UUID publico del asiento
 * @param patientId        paciente titular
 * @param type             tipo de asiento
 * @param points           puntos del asiento; positivo acumula, negativo descuenta
 * @param resultingBalance saldo tras aplicarlo
 * @param valueBob         equivalente en bolivianos de los puntos del asiento
 * @param invoiceId        factura que lo origino, cuando aplica
 * @param appointmentId    cita que lo origino, cuando aplica
 * @param reason           motivo declarado
 * @param occurredAt       instante del asiento
 */
public record LoyaltyMovementResponse(
        UUID id,
        UUID patientId,
        LoyaltyMovementType type,
        int points,
        int resultingBalance,
        BigDecimal valueBob,
        UUID invoiceId,
        UUID appointmentId,
        String reason,
        OffsetDateTime occurredAt) {
}
