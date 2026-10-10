package com.medicalsys.identidad.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El orden de los roles no importa: (A, B) y (B, A) son el mismo par. */
public record RoleIncompatibilityRequest(
        @NotBlank @Size(max = 30) String roleA,
        @NotBlank @Size(max = 30) String roleB,
        @NotBlank @Size(max = 300) String reason) {
}
