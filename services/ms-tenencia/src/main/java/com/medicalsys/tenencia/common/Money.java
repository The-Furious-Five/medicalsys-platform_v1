package com.medicalsys.tenencia.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de dinero de la plataforma: {@code DECIMAL} / {@link BigDecimal}, nunca coma flotante.
 *
 * <p>ms-tenencia publica el precio que otros servicios facturan. Si un precio llegara como
 * {@code double}, dos consumidores podrian leer importes distintos para la misma prestacion. Todo
 * importe pasa por {@link #amount(BigDecimal)} y queda con los dos decimales del diccionario.
 */
public final class Money {

    /** Escala de los importes: DECIMAL(10,2) y DECIMAL(12,2) en tenancy_db. */
    public static final int AMOUNT_SCALE = 2;

    /** Redondeo comercial, el mismo que aplica el motor al guardar el DECIMAL. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(AMOUNT_SCALE);

    private Money() {
    }

    /** Normaliza un importe a dos decimales; {@code null} se interpreta como cero. */
    public static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.setScale(AMOUNT_SCALE, ROUNDING);
    }

    /** Comparacion por valor: {@code 150.00} y {@code 150.0} son el mismo importe. */
    public static boolean equalValue(BigDecimal left, BigDecimal right) {
        return amount(left).compareTo(amount(right)) == 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return amount(value).signum() < 0;
    }
}
