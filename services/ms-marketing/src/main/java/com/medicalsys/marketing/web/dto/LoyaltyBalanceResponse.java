package com.medicalsys.marketing.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Saldo de puntos de un paciente, leido de la vista {@code v_loyalty_balance}.
 *
 * <p>El saldo se deriva de la suma de asientos del libro mayor; no existe columna de saldo que
 * alguien pueda corregir a mano y dejar descuadrada respecto de sus movimientos.
 *
 * @param patientId        paciente consultado
 * @param fullName         nombre completo replicado
 * @param programId        programa al que pertenece el saldo
 * @param programName      nombre del programa
 * @param balancePoints    saldo en puntos
 * @param balanceValueBob  equivalente en bolivianos segun la tasa de canje vigente
 * @param lastMovementAt   instante del ultimo asiento
 */
public record LoyaltyBalanceResponse(
        UUID patientId,
        String fullName,
        UUID programId,
        String programName,
        int balancePoints,
        BigDecimal balanceValueBob,
        OffsetDateTime lastMovementAt) {
}
