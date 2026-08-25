package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.RequiredResourceType;
import com.medicalsys.tenencia.domain.enums.ServiceOfferingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Actualizacion de una prestacion; el codigo no se edita.
 *
 * <p>Cambiar el precio aqui no altera ninguna factura ya emitida: ms-facturacion congela el
 * snapshot de codigo, descripcion y precio en {@code invoice_item}.
 */
public record UpdateServiceOfferingRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 500) String description,
        @NotNull ServiceOfferingType type,
        @Size(max = 20) String specialtyCode,
        @Min(5) @Max(720) Integer durationMinutes,
        @NotNull @DecimalMin(value = "0.00", message = "no puede ser negativo")
                @Digits(integer = 10, fraction = 2) BigDecimal basePriceBob,
        boolean requiresConsent,
        UUID consentTemplatePublicId,
        RequiredResourceType requiredResourceType,
        @Size(max = 30) String taxProductCode,
        @Size(max = 10) String taxUnitCode) {
}
