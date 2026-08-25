package com.medicalsys.tenencia.web.controller;

import com.medicalsys.tenencia.common.security.RequiresRole;
import com.medicalsys.tenencia.common.security.Role;
import com.medicalsys.tenencia.common.tenant.RequestContextHolder;
import com.medicalsys.tenencia.domain.enums.BranchStatus;
import com.medicalsys.tenencia.service.BranchService;
import com.medicalsys.tenencia.web.dto.BranchResponse;
import com.medicalsys.tenencia.web.dto.ChangeBranchStatusRequest;
import com.medicalsys.tenencia.web.dto.CreateBranchRequest;
import com.medicalsys.tenencia.web.dto.PageResponse;
import com.medicalsys.tenencia.web.dto.UpdateBranchRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sedes fisicas de la organizacion del token.
 *
 * <p>Crear una sucursal consume la cuota BRANCHES del plan contratado y devuelve 422 cuando el
 * limite ya esta alcanzado.
 */
@RestController
@RequestMapping("/api/v1/tenancy/sucursales")
public class BranchController {

    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public ResponseEntity<BranchResponse> create(@Valid @RequestBody CreateBranchRequest request) {
        BranchResponse branch = service.create(RequestContextHolder.require(), request);
        return ResponseEntity.created(URI.create("/api/v1/tenancy/sucursales/" + branch.id())).body(branch);
    }

    /** Lista las sedes del tenant; con {@code status} devuelve solo las de ese estado. */
    @GetMapping
    @RequiresRole({Role.ADMINISTRATOR, Role.RECEPTIONIST, Role.BILLING_CLERK, Role.PHYSICIAN, Role.PLATFORM_ADMIN})
    public PageResponse<BranchResponse> list(@RequestParam(required = false) BranchStatus status,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(service.list(RequestContextHolder.require(), status, pageable));
    }

    @GetMapping("/{branchId}")
    @RequiresRole({Role.ADMINISTRATOR, Role.RECEPTIONIST, Role.BILLING_CLERK, Role.PHYSICIAN, Role.PLATFORM_ADMIN})
    public BranchResponse get(@PathVariable UUID branchId) {
        return service.get(RequestContextHolder.require(), branchId);
    }

    @PutMapping("/{branchId}")
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public BranchResponse update(@PathVariable UUID branchId, @Valid @RequestBody UpdateBranchRequest request) {
        return service.update(RequestContextHolder.require(), branchId, request);
    }

    /** Da de alta o de baja la sede; la baja es logica y libera cuota del plan. */
    @PatchMapping("/{branchId}/estado")
    @RequiresRole({Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public BranchResponse changeStatus(@PathVariable UUID branchId,
            @Valid @RequestBody ChangeBranchStatusRequest request) {
        return service.changeStatus(RequestContextHolder.require(), branchId, request);
    }
}
