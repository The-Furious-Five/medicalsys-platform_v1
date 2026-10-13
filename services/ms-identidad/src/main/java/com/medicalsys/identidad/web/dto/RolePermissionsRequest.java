package com.medicalsys.identidad.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Conjunto completo de permisos del rol: reemplaza al anterior. Una lista vacia los quita todos. */
public record RolePermissionsRequest(
        @NotNull List<@NotBlank String> permissions) {
}
