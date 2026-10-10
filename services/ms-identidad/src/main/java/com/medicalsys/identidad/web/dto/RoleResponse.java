package com.medicalsys.identidad.web.dto;

import com.medicalsys.identidad.domain.RoleScope;
import java.util.List;

public record RoleResponse(
        String code,
        String name,
        String description,
        RoleScope scope,
        boolean system,
        boolean active,
        List<PermissionResponse> permissions,
        List<String> assignableRoles) {
}
