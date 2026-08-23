package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.PermissionModule;

public record PermissionResponse(
        String code,
        PermissionModule module,
        String description,
        boolean sensitive) {
}
