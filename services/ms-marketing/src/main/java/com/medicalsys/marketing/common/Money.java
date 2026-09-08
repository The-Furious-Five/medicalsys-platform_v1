package com.medicalsys.marketing.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de dinero de la plataforma: {@code DECIMAL} / {@link BigDecimal}, nunca coma flotante.
 *
 * <p>Aqui el dinero aparece en tres sitios: el gasto acumulado del paciente, el descuento que
 * calcula un cupon y el valor en bolivianos de los puntos de fidelidad. Los tres pasan por
 * {@link #amount(BigDecimal)} para quedar con dos decimales exactos, de modo que el descuento que
 * ms-marketing responde a ms-facturacion sea el mismo centavo que la factura termina cobrando.
 */
public final class Money {

    /** Escala de los importes: DECIMAL(14,2) y DECIMAL(12,2) en marketing_db. */
    public static final int AMOUNT_SCALE = 2;

    /** Escala de las tasas del programa de fidelidad: DECIMAL(8,4). */
    public static final int RATE_SCALE = 4;

    /** Redondeo comercial, el mismo que aplica el motor al guardar el DECIMAL. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(AMOUNT_SCALE);

    private Money() {
    }

    /** Normaliza un importe a dos decimales; {@code null} se interpreta como cero. */
    public static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.setScale(AMOUNT_SCALE, ROUNDING);
    }

    /** Normaliza una tasa a cuatro decimales; {@code null} se interpreta como cero. */
    public static BigDecimal rate(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(RATE_SCALE) : value.setScale(RATE_SCALE, ROUNDING);
    }

    /** Suma normalizada de dos importes. */
    public static BigDecimal add(BigDecimal left, BigDecimal right) {
        return amount(amount(left).add(amount(right)));
    }

    /** Resta normalizada de dos importes, acotada a cero: un descuento no vuelve negativo el total. */
    public static BigDecimal subtractFloorZero(BigDecimal left, BigDecimal right) {
        BigDecimal result = amount(left).subtract(amount(right));
        return result.signum() < 0 ? ZERO : amount(result);
    }

    /** Aplica un porcentaje sobre una base. */
    public static BigDecimal percentageOf(BigDecimal base, BigDecimal percentage) {
        return amount(amount(base).multiply(amount(percentage)).divide(BigDecimal.valueOf(100), AMOUNT_SCALE,
                ROUNDING));
    }

    /** Devuelve el menor de dos importes normalizados. */
    public static BigDecimal min(BigDecimal left, BigDecimal right) {
        return amount(left).min(amount(right));
    }

    public static boolean isNegative(BigDecimal value) {
        return amount(value).signum() < 0;
    }
}
