package com.medicalsys.tenencia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalsys.tenencia.domain.enums.OrganizationType;
import com.medicalsys.tenencia.domain.enums.UsageMetric;
import com.medicalsys.tenencia.domain.model.Organization;
import com.medicalsys.tenencia.domain.model.PlanUsage;
import com.medicalsys.tenencia.domain.model.SubscriptionPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Reglas de limite del plan, sin base de datos.
 *
 * <p>El punto delicado es la diferencia entre "sin limite" y "limite cero": el diccionario define
 * NULL como ilimitado, de modo que confundirlos dejaria a un plan sin restricciones bloqueado o,
 * peor, a uno restringido sin control.
 */
class PlanLimitsTest {

    private static final BigDecimal PRICE = new BigDecimal("350.00");

    @Nested
    @DisplayName("limites declarados por el plan")
    class Limits {

        @Test
        @DisplayName("un limite nulo significa ilimitado, no cero")
        void nullMeansUnlimited() {
            SubscriptionPlan plan = SubscriptionPlan.of("PRO", "Plan profesional", PRICE, null, null, null,
                    true, true);

            assertThat(plan.limitFor(UsageMetric.USERS)).isEmpty();
            assertThat(plan.limitFor(UsageMetric.BRANCHES)).isEmpty();
            assertThat(plan.limitFor(UsageMetric.STORAGE_BYTES)).isEmpty();
        }

        @Test
        @DisplayName("un limite cero es un limite real y no se confunde con ilimitado")
        void zeroIsARealLimit() {
            SubscriptionPlan plan = SubscriptionPlan.of("FREE", "Plan gratuito", BigDecimal.ZERO, 0, 0, 0,
                    false, false);

            assertThat(plan.limitFor(UsageMetric.USERS)).contains(0L);
            assertThat(plan.limitFor(UsageMetric.BRANCHES)).contains(0L);
        }

        @Test
        @DisplayName("el almacenamiento del plan se declara en GB y se compara en bytes")
        void storageLimitIsConvertedToBytes() {
            SubscriptionPlan plan = SubscriptionPlan.of("BASIC", "Plan basico", PRICE, 10, 2, 5, true, true);

            assertThat(plan.limitFor(UsageMetric.STORAGE_BYTES)).contains(5L * UsageMetric.BYTES_PER_GB);
            assertThat(plan.limitFor(UsageMetric.BRANCHES)).contains(2L);
        }
    }

    @Nested
    @DisplayName("precio del plan")
    class Price {

        @Test
        @DisplayName("el precio se normaliza a dos decimales y nunca usa coma flotante")
        void priceKeepsTwoDecimals() {
            SubscriptionPlan plan = SubscriptionPlan.of("BASIC", "Plan basico", new BigDecimal("350.005"),
                    10, 2, 5, true, true);

            assertThat(plan.getMonthlyPriceBob()).isEqualByComparingTo("350.01");
            assertThat(plan.getMonthlyPriceBob().scale()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("valor materializado del consumo")
    class Usage {

        private PlanUsage usage() {
            Organization organization = Organization.onboard("DEMO", OrganizationType.PRIVATE_CLINIC,
                    "CLINICA DEMO S.R.L.", "Clinica Demo", "0000000000", "contacto@demo.invalid",
                    "America/La_Paz", "BOB");
            return PlanUsage.start(organization, UsageMetric.USERS, LocalDate.of(2026, 1, 1), null);
        }

        @Test
        @DisplayName("el contador arranca en cero y suma los incrementos reportados")
        void accumulatesReportedDeltas() {
            PlanUsage usage = usage();
            assertThat(usage.getCurrentValue()).isZero();

            usage.add(3);
            usage.add(2);

            assertThat(usage.getCurrentValue()).isEqualTo(5);
        }

        @Test
        @DisplayName("un decremento excesivo se recorta a cero y no viola el CHECK de la base")
        void neverGoesBelowZero() {
            PlanUsage usage = usage();
            usage.add(1);

            usage.add(-5);

            assertThat(usage.getCurrentValue()).isZero();
        }

        @Test
        @DisplayName("wouldExceed compara el consumo mas el delta contra el limite")
        void detectsWhenTheLimitWouldBeExceeded() {
            PlanUsage usage = usage();
            usage.add(4);

            assertThat(usage.wouldExceed(1, 5)).isFalse();
            assertThat(usage.wouldExceed(2, 5)).isTrue();
        }

        @Test
        @DisplayName("un recuento absoluto negativo se rechaza")
        void negativeCountIsRejected() {
            assertThatThrownBy(() -> usage().set(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
