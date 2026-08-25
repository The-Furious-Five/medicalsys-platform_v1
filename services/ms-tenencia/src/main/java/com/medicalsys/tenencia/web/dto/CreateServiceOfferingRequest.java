package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.RequiredResourceType;
import com.medicalsys.tenencia.domain.enums.ServiceOfferingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Alta de una prestacion en el catalogo del tenant.
 *
 * <p>{@code durationMinutes} replica el CHECK del diccionario (entre 5 y 720) y
 * {@code basePriceBob} el CHECK de importe no negativo, para devolver un 400 con el campo exacto
 * antes de que el motor responda con un error generico.
 *
 * @param code                     codigo de la prestacion, unico dentro de la organizacion
 * @param name                     descripcion impresa en el detalle de la factura
 * @param type                     tipo de prestacion
 * @param specialtyCode            referencia externa a specialty.code de catalogs_db
 * @param durationMinutes          duracion que ms-agenda reserva
 * @param basePriceBob             precio unitario en bolivianos
 * @param requiresConsent          exige consentimiento informado
 * @param consentTemplatePublicId  plantilla de consentimiento en documents_db
 * @param requiredResourceType     recurso fisico que ms-agenda debe reservar
 * @param taxProductCode           codigo de producto/servicio homologado del SIN
 * @param taxUnitCode              unidad de medida del catalogo del SIN; 58 = unidad
 */
public record CreateServiceOfferingRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+",
                message = "solo admite letras, digitos, guion y guion bajo") String code,
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
