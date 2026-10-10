package com.medicalsys.identidad.web;

import com.medicalsys.identidad.domain.PermissionModule;
import com.medicalsys.identidad.service.RoleCatalogService;
import com.medicalsys.identidad.service.RoleManagementService;
import com.medicalsys.identidad.web.dto.CreateRoleRequest;
import com.medicalsys.identidad.web.dto.PermissionResponse;
import com.medicalsys.identidad.web.dto.RoleAssignableRolesRequest;
import com.medicalsys.identidad.web.dto.RolePermissionsRequest;
import com.medicalsys.identidad.web.dto.RoleResponse;
import com.medicalsys.identidad.web.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity")
public class RoleController {

    private final RoleCatalogService catalog;
    private final RoleManagementService management;

    public RoleController(RoleCatalogService catalog, RoleManagementService management) {
        this.catalog = catalog;
        this.management = management;
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles() {
        return catalog.listRoles();
    }

    @GetMapping("/roles/{code}")
    public RoleResponse role(@PathVariable String code) {
        return catalog.getRole(code);
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(Authentication authentication,
                                               @Valid @RequestBody CreateRoleRequest request) {
        RoleResponse created = management.create(request, Actors.publicId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/identity/roles/" + created.code())).body(created);
    }

    @PatchMapping("/roles/{code}")
    public RoleResponse update(Authentication authentication, @PathVariable String code,
                               @Valid @RequestBody UpdateRoleRequest request) {
        return management.update(code, request, Actors.publicId(authentication));
    }

    /** Desactiva el rol; no lo borra. */
    @DeleteMapping("/roles/{code}")
    public RoleResponse deactivate(Authentication authentication, @PathVariable String code) {
        return management.deactivate(code, Actors.publicId(authentication));
    }

    @PutMapping("/roles/{code}/permissions")
    public RoleResponse replacePermissions(Authentication authentication, @PathVariable String code,
                                           @Valid @RequestBody RolePermissionsRequest request) {
        return management.replacePermissions(code, request, Actors.publicId(authentication));
    }

    @PutMapping("/roles/{code}/assignable-roles")
    public RoleResponse replaceAssignableRoles(Authentication authentication, @PathVariable String code,
                                               @Valid @RequestBody RoleAssignableRolesRequest request) {
        return management.replaceAssignableRoles(code, request, Actors.publicId(authentication));
    }

    @GetMapping("/permissions")
    public List<PermissionResponse> permissions(@RequestParam(required = false) PermissionModule module) {
        return catalog.listPermissions(module);
    }
}
