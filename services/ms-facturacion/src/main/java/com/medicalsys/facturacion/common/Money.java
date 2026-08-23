package com.medicalsys.facturacion.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de dinero de la plataforma: {@code DECIMAL} / {@link BigDecimal}, nunca coma flotante.
 *
 * <p>Todo importe monetario que llega a la base o a la respuesta pasa por {@link #amount(BigDecimal)}
 * para quedar con dos decimales exactos. Sin esa normalizacion, {@code 3 x 33.333} y la suma de los
 * subtotales pueden diferir en un centavo y la factura dejaria de cuadrar con su detalle.
 */
public final class Money {

    /** Escala de los importes: DECIMAL(14,2) en billing_db. */
    public static final int AMOUNT_SCALE = 2;

    /** Escala del tipo de cambio: DECIMAL(12,5) en billing_db. */
    public static final int EXCHANGE_RATE_SCALE = 5;

    /** Escala de las cantidades facturadas: DECIMAL(12,4) en billing_db. */
    public static final int QUANTITY_SCALE = 4;

    /** Redondeo comercial, el mismo que aplica el motor al guardar el DECIMAL. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(AMOUNT_SCALE);

    private Money() {
    }

    /** Normaliza un importe a dos decimales; {@code null} se interpreta como cero. */
    public static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.setScale(AMOUNT_SCALE, ROUNDING);
    }

    /** Normaliza una cantidad facturada a cuatro decimales; {@code null} se interpreta como uno. */
    public static BigDecimal quantity(BigDecimal value) {
        return value == null ? BigDecimal.ONE.setScale(QUANTITY_SCALE) : value.setScale(QUANTITY_SCALE, ROUNDING);
    }

    /** Normaliza un tipo de cambio a cinco decimales; {@code null} se interpreta como paridad. */
    public static BigDecimal exchangeRate(BigDecimal value) {
        return value == null
                ? BigDecimal.ONE.setScale(EXCHANGE_RATE_SCALE)
                : value.setScale(EXCHANGE_RATE_SCALE, ROUNDING);
    }

    /** Suma normalizada de dos importes. */
    public static BigDecimal add(BigDecimal left, BigDecimal right) {
        return amount(amount(left).add(amount(right)));
    }

    /** Resta normalizada de dos importes. */
    public static BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return amount(amount(left).subtract(amount(right)));
    }

    /** Producto normalizado de una cantidad por un precio unitario. */
    public static BigDecimal multiply(BigDecimal quantity, BigDecimal unitPrice) {
        return amount(quantity(quantity).multiply(amount(unitPrice)));
    }

    /** Comparacion por valor: {@code 150.00} y {@code 150.0} son el mismo importe. */
    public static boolean equalValue(BigDecimal left, BigDecimal right) {
        return amount(left).compareTo(amount(right)) == 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return amount(value).signum() < 0;
    }
}
