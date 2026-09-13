package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.Department;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Actualizacion de una sede; el codigo no se edita porque otras bases ya lo referencian. */
public record UpdateBranchRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 250) String address,
        @NotBlank @Size(max = 80) String city,
        @NotNull Department department,
        @Size(max = 20) @Pattern(regexp = "\\+?[0-9]{6,19}", message = "debe estar en formato E.164")
                String phoneE164,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        String openingHours) {
}
