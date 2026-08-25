package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.Department;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Alta de una sede fisica.
 *
 * @param code          codigo de la sucursal, unico dentro de la organizacion
 * @param name          nombre visible
 * @param headquarters  indica si es la casa matriz; solo puede haber una
 * @param address       direccion fisica
 * @param city          ciudad
 * @param department    departamento de Bolivia
 * @param openingHours  horario referencial en JSON mostrado al paciente
 */
public record CreateBranchRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+",
                message = "solo admite letras, digitos, guion y guion bajo") String code,
        @NotBlank @Size(max = 120) String name,
        boolean headquarters,
        @NotBlank @Size(max = 250) String address,
        @NotBlank @Size(max = 80) String city,
        @NotNull Department department,
        @Size(max = 20) @Pattern(regexp = "\\+?[0-9]{6,19}", message = "debe estar en formato E.164")
                String phoneE164,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        String openingHours) {
}
