package com.medicalsys.identidad.web;

import com.medicalsys.identidad.domain.PermissionModule;
import com.medicalsys.identidad.domain.RoleCode;
import com.medicalsys.identidad.service.RoleCatalogService;
import com.medicalsys.identidad.web.dto.PermissionResponse;
import com.medicalsys.identidad.web.dto.RoleResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity")
public class RoleController {

    private final RoleCatalogService catalog;

    public RoleController(RoleCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles() {
        return catalog.listRoles();
    }

    @GetMapping("/roles/{code}")
    public RoleResponse role(@PathVariable RoleCode code) {
        return catalog.getRole(code);
    }

    @GetMapping("/permissions")
    public List<PermissionResponse> permissions(@RequestParam(required = false) PermissionModule module) {
        return catalog.listPermissions(module);
    }
}
