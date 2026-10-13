package com.medicalsys.identidad.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Campos ausentes no cambian. {@code active=false} equivale a DELETE; {@code true} reactiva. */
public record UpdateRoleRequest(
        @Size(max = 80) @Pattern(regexp = ".*\\S.*", message = "no puede estar en blanco") String name,
        @Size(max = 300) String description,
        Boolean active) {
}
