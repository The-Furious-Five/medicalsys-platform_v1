package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.LoyaltyMovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Asiento registrado por una persona: canje, ajuste manual o bonificacion.
 *
 * <p>{@code points} se pide siempre positivo y es el tipo el que decide el signo. Pedir un numero
 * negativo para canjear invita a equivocarse en el signo justo en la operacion que resta saldo.
 *
 * <p>La acumulacion por consumo no pasa por aqui: llega por evento desde ms-facturacion, con su
 * clave de idempotencia. Permitir acumular a mano abriria la puerta a duplicar puntos ya otorgados.
 *
 * @param patientId paciente titular del saldo
 * @param type      tipo de asiento; ACCRUAL no se admite por esta ruta
 * @param points    cantidad de puntos, siempre positiva
 * @param reason    motivo, obligatorio para el ajuste manual
 */
public record LoyaltyMovementRequest(
        @NotNull(message = "es obligatorio")
        UUID patientId,

        @NotNull(message = "es obligatorio")
        LoyaltyMovementType type,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser positivo; el tipo de asiento decide el signo")
        Integer points,

        @Size(max = 250, message = "debe tener como maximo 250 caracteres")
        String reason) {
}
