package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.Department;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Actualizacion de los datos administrativos del tenant.
 *
 * <p>{@code code} y {@code nit} no se editan: identifican al tenant en el subdominio y ante el SIN,
 * y otras bases ya guardan referencias a el.
 */
public record UpdateOrganizationRequest(
        @NotBlank @Size(max = 180) String legalName,
        @NotBlank @Size(max = 180) String tradeName,
        @NotBlank @Email @Size(max = 160) String contactEmail,
        @Size(max = 20) @Pattern(regexp = "\\+?[0-9]{6,19}", message = "debe estar en formato E.164")
                String phoneE164,
        @Size(max = 80) String city,
        Department department,
        @Size(max = 40) String timeZone,
        @Size(max = 500) String logoUrl,
        @Size(max = 40) String tradeRegistryNumber) {
}
