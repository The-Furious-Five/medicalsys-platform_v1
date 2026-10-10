package com.medicalsys.identidad.web;

import com.medicalsys.identidad.service.RoleManagementService;
import com.medicalsys.identidad.web.dto.RoleIncompatibilityRequest;
import com.medicalsys.identidad.web.dto.RoleIncompatibilityResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pares de roles que una misma cuenta no puede tener a la vez (segregacion de funciones). */
@RestController
@RequestMapping("/api/v1/identity/role-incompatibilities")
public class RoleIncompatibilityController {

    private final RoleManagementService management;

    public RoleIncompatibilityController(RoleManagementService management) {
        this.management = management;
    }

    @GetMapping
    public List<RoleIncompatibilityResponse> list() {
        return management.listIncompatibilities();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleIncompatibilityResponse declare(Authentication authentication,
                                               @Valid @RequestBody RoleIncompatibilityRequest request) {
        return management.declareIncompatible(request, Actors.publicId(authentication));
    }

    @DeleteMapping("/{roleA}/{roleB}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication authentication, @PathVariable String roleA, @PathVariable String roleB) {
        management.removeIncompatibility(roleA, roleB, Actors.publicId(authentication));
    }
}
