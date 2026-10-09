package com.medicalsys.tenencia.web.dto;

import com.medicalsys.tenencia.domain.enums.Department;
import com.medicalsys.tenencia.domain.enums.OrganizationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de un tenant.
 *
 * <p>El {@code publicId} no viaja en el cuerpo: lo genera el servicio. Aceptarlo desde el cliente
 * permitiria elegir la identidad de un tenant existente.
 *
 * @param code         slug del tenant usado en el subdominio; unico en la plataforma
 * @param type         tipo de organizacion
 * @param legalName    razon social
 * @param tradeName    nombre comercial
 * @param nit          NIT registrado en el SIN; unico en la plataforma
 * @param contactEmail correo de contacto administrativo
 * @param phoneE164    telefono en formato E.164
 * @param city         ciudad
 * @param department   departamento de Bolivia
 * @param timeZone     zona horaria; si falta se usa la de configuracion
 * @param currency     moneda ISO-4217; si falta se usa la de configuracion
 * @param planCode     plan a contratar; si falta se usa el plan por defecto
 */
public record CreateOrganizationRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+",
                message = "solo admite letras, digitos, guion y guion bajo") String code,
        @NotNull OrganizationType type,
        @NotBlank @Size(max = 180) String legalName,
        @NotBlank @Size(max = 180) String tradeName,
        @NotBlank @Size(max = 15) @Pattern(regexp = "[0-9]+", message = "solo admite digitos") String nit,
        @NotBlank @Email @Size(max = 160) String contactEmail,
        @Size(max = 20) @Pattern(regexp = "\\+?[0-9]{6,19}", message = "debe estar en formato E.164")
                String phoneE164,
        @Size(max = 80) String city,
        Department department,
        @Size(max = 40) String timeZone,
        @Size(min = 3, max = 3) String currency,
        @Size(max = 30) String planCode) {
}
