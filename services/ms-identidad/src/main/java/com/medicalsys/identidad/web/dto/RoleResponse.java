package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.RoleCode;
import com.medicalsys.identidad.domain.RoleScope;
import java.util.List;

public record RoleResponse(
        RoleCode code,
        String name,
        String description,
        RoleScope scope,
        boolean system,
        List<PermissionResponse> permissions) {
}
