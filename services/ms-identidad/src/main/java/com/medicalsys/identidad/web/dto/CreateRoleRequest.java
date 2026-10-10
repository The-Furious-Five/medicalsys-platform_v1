package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.RoleScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** El rol nace activo y sin permisos; se le asignan con PUT /roles/{code}/permissions. */
public record CreateRoleRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,29}$",
                message = "debe tener de 3 a 30 caracteres en MAYUSCULAS, digitos o guion bajo, y empezar con letra")
        String code,
        @NotBlank @Size(max = 80) String name,
        @Size(max = 300) String description,
        RoleScope scope) {
}
