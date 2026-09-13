package com.medicalsys.marketing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.marketing.domain.enums.DiscountType;
import com.medicalsys.marketing.domain.model.Promotion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Calculo del descuento, probado sin Spring ni base.
 *
 * <p>Es aritmetica de dinero y se ejecuta en cada compilacion. Importa que el numero cuadre al
 * centavo: es el mismo importe que ms-facturacion va a restar de la factura, y una diferencia de
 * redondeo aqui deja una factura que no cuadra con su detalle.
 */
class PromotionDiscountTest {

    @ParameterizedTest(name = "{0} de {1} sobre {2} BOB descuenta {3} BOB")
    @CsvSource({
            "PERCENTAGE,   25,     200.00, 50.00",
            "PERCENTAGE,   10,     150.00, 15.00",
            "PERCENTAGE,   100,    150.00, 150.00",
            "FIXED_AMOUNT, 30.00,  150.00, 30.00",
            "FIXED_AMOUNT, 500.00, 80.00,  80.00",
            "FREE_SERVICE, 0,      250.00, 250.00"})
    @DisplayName("el descuento se calcula segun el tipo y nunca supera la base")
    void calculatesDiscount(DiscountType type, BigDecimal value, BigDecimal base, BigDecimal expected) {
        Promotion promotion = promotion(type, value);

        assertThat(promotion.discountFor(base)).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("un porcentaje mayor que 100 se acota: no existe descuento del 250 %")
    void capsPercentageAtOneHundred() {
        Promotion promotion = promotion(DiscountType.PERCENTAGE, new BigDecimal("250"));

        assertThat(promotion.discountFor(new BigDecimal("100.00"))).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("el redondeo es comercial a dos decimales")
    void roundsToTwoDecimals() {
        Promotion promotion = promotion(DiscountType.PERCENTAGE, new BigDecimal("33"));

        // 33 % de 10.01 = 3.3033 -> 3.30
        assertThat(promotion.discountFor(new BigDecimal("10.01"))).isEqualByComparingTo("3.30");
    }

    @Test
    @DisplayName("la vigencia es inclusiva en ambos extremos")
    void validityIsInclusive() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate until = LocalDate.of(2026, 9, 30);
        Promotion promotion = Promotion.create(UUID.randomUUID(), "VIGENCIA", "Cupon", null,
                DiscountType.PERCENTAGE, BigDecimal.TEN, null, null, 1, from, until, null);

        assertThat(promotion.isValidOn(from)).isTrue();
        assertThat(promotion.isValidOn(until)).isTrue();
        assertThat(promotion.isValidOn(from.minusDays(1))).isFalse();
        assertThat(promotion.isValidOn(until.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("sin cupo maximo el cupon nunca se agota")
    void unlimitedCouponNeverRunsOut() {
        Promotion promotion = promotion(DiscountType.PERCENTAGE, BigDecimal.TEN);
        for (int use = 0; use < 1000; use++) {
            promotion.registerRedemption();
        }

        assertThat(promotion.hasRemainingRedemptions()).isTrue();
    }

    private Promotion promotion(DiscountType type, BigDecimal value) {
        return Promotion.create(UUID.randomUUID(), "CUPON", "Cupon de prueba", null, type, value, null,
                null, 1, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), null);
    }
}
