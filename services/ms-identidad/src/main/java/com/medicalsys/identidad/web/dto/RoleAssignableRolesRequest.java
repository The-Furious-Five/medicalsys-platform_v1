package com.medicalsys.identidad.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Conjunto completo de roles que el rol puede asignar: reemplaza al anterior. Vacia, no delega ninguno. */
public record RoleAssignableRolesRequest(
        @NotNull List<@NotBlank String> roles) {
}
