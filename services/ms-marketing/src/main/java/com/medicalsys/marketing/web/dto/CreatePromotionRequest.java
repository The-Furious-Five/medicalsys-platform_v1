package com.medicalsys.marketing.web.dto;

import com.medicalsys.marketing.domain.enums.DiscountType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Alta de un cupon.
 *
 * <p>El codigo se restringe a mayusculas, digitos, guion y guion bajo. Un cupon se dicta por
 * telefono y se escribe en un mostrador: admitir espacios o acentos garantiza validaciones fallidas
 * que el paciente vive como un error del sistema.
 *
 * @param couponCode            codigo que el paciente presenta
 * @param name                  nombre visible de la promocion
 * @param description           descripcion libre
 * @param discountType          forma del descuento
 * @param value                 porcentaje o monto, segun el tipo
 * @param serviceOfferingId     prestacion a la que aplica; nulo para cualquiera
 * @param maxRedemptions        cupo global; nulo para ilimitado
 * @param redemptionsPerPatient cuantas veces puede usarlo el mismo paciente
 * @param validFrom             inicio de la vigencia
 * @param validUntil            fin de la vigencia, inclusive
 */
public record CreatePromotionRequest(
        @NotBlank(message = "no debe estar vacio")
        @Size(max = 30, message = "debe tener como maximo 30 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "solo admite letras, digitos, guion y guion bajo")
        String couponCode,

        @NotBlank(message = "no debe estar vacio")
        @Size(max = 160, message = "debe tener como maximo 160 caracteres")
        String name,

        @Size(max = 500, message = "debe tener como maximo 500 caracteres")
        String description,

        @NotNull(message = "es obligatorio")
        DiscountType discountType,

        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        BigDecimal value,

        UUID serviceOfferingId,

        @Min(value = 1, message = "debe ser al menos 1")
        Long maxRedemptions,

        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = 255, message = "debe ser como maximo 255")
        Integer redemptionsPerPatient,

        @NotNull(message = "es obligatoria")
        LocalDate validFrom,

        @NotNull(message = "es obligatoria")
        LocalDate validUntil) {
}
