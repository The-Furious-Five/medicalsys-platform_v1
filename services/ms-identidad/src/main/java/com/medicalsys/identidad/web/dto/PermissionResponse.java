package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.PermissionModule;
import com.medicalsys.identidad.domain.SecurityLevel;

public record PermissionResponse(
        String code,
        PermissionModule module,
        String description,
        boolean sensitive,
        SecurityLevel classification) {
}
