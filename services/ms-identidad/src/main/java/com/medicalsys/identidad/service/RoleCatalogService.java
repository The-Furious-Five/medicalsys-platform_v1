package com.medicalsys.identidad.service;

import com.medicalsys.identidad.domain.PermissionModule;
import com.medicalsys.identidad.domain.RoleCode;
import com.medicalsys.identidad.repository.PermissionRepository;
import com.medicalsys.identidad.repository.RoleRepository;
import com.medicalsys.identidad.web.dto.PermissionResponse;
import com.medicalsys.identidad.web.dto.RoleResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleCatalogService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final IdentityMapper mapper;

    public RoleCatalogService(RoleRepository roles, PermissionRepository permissions, IdentityMapper mapper) {
        this.roles = roles;
        this.permissions = permissions;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roles.findAll().stream()
                .sorted(Comparator.comparing(role -> role.getCode().name()))
                .map(role -> {
                    role.getPermissions().size();
                    return mapper.toRoleResponse(role);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(RoleCode code) {
        return roles.findByCode(code)
                .map(mapper::toRoleResponse)
                .orElseThrow(() -> ApiException.notFound("No existe el rol " + code));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions(PermissionModule module) {
        var found = module == null
                ? permissions.findAllByOrderByCodeAsc()
                : permissions.findAllByModuleOrderByCodeAsc(module);
        return found.stream().map(mapper::toPermissionResponse).toList();
    }
}
