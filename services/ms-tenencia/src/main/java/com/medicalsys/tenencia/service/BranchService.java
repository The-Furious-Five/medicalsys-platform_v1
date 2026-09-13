package com.medicalsys.tenencia.service;

import com.medicalsys.tenencia.common.error.ConflictException;
import com.medicalsys.tenencia.common.error.ErrorCode;
import com.medicalsys.tenencia.common.error.NotFoundException;
import com.medicalsys.tenencia.common.tenant.RequestContext;
import com.medicalsys.tenencia.domain.enums.BranchStatus;
import com.medicalsys.tenencia.domain.enums.UsageMetric;
import com.medicalsys.tenencia.domain.model.Branch;
import com.medicalsys.tenencia.domain.model.Organization;
import com.medicalsys.tenencia.mapper.TenancyMapper;
import com.medicalsys.tenencia.repository.BranchRepository;
import com.medicalsys.tenencia.web.dto.BranchRef;
import com.medicalsys.tenencia.web.dto.BranchResponse;
import com.medicalsys.tenencia.web.dto.ChangeBranchStatusRequest;
import com.medicalsys.tenencia.web.dto.CreateBranchRequest;
import com.medicalsys.tenencia.web.dto.UpdateBranchRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de las sedes fisicas.
 *
 * <p>Crear una sucursal consume la cuota BRANCHES del plan. La reserva ocurre antes de persistir y
 * dentro de la misma transaccion: si el plan no admite otra sede, no queda ni la fila ni el
 * consumo.
 *
 * <p>Toda consulta lleva el {@code organizationId} del contexto. Pedir la sucursal de otra clinica
 * conociendo su UUID devuelve 404, no 403: confirmar su existencia ya seria una fuga.
 */
@Service
public class BranchService {

    private static final Logger log = LoggerFactory.getLogger(BranchService.class);

    private final BranchRepository branches;
    private final OrganizationService organizations;
    private final PlanUsageService planUsage;
    private final TenancyMapper mapper;

    public BranchService(BranchRepository branches, OrganizationService organizations, PlanUsageService planUsage,
            TenancyMapper mapper) {
        this.branches = branches;
        this.organizations = organizations;
        this.planUsage = planUsage;
        this.mapper = mapper;
    }

    /**
     * Registra una sede de la organizacion del contexto.
     *
     * @throws ConflictException si el codigo ya existe en la organizacion o ya hay casa matriz
     */
    @Transactional
    public BranchResponse create(RequestContext context, CreateBranchRequest request) {
        UUID organizationId = context.organizationId();
        Organization organization = organizations.requireOperational(organizationId);

        String code = Branch.normalizeCode(request.code());
        if (branches.existsByOrganizationPublicIdAndCode(organizationId, code)) {
            throw new ConflictException(ErrorCode.BRANCH_CODE_ALREADY_USED,
                    "Ya existe una sucursal con el codigo '" + code + "' en esta organizacion");
        }
        if (request.headquarters()
                && branches.findByOrganizationPublicIdAndHeadquartersTrue(organizationId).isPresent()) {
            throw new ConflictException(ErrorCode.HEADQUARTERS_ALREADY_DEFINED,
                    "La organizacion ya tiene una casa matriz declarada");
        }

        planUsage.reserve(organizationId, UsageMetric.BRANCHES, 1);

        Branch branch = Branch.open(organization, code, request.name(), request.headquarters(), request.address(),
                request.city(), request.department());
        branch.updateDetails(request.name(), request.address(), request.city(), request.department(),
                request.phoneE164(), request.latitude(), request.longitude(), request.openingHours());
        branches.save(branch);

        log.info("Sucursal registrada organizationId={} branchId={} code={}", organizationId, branch.getPublicId(),
                code);
        return mapper.toResponse(branch);
    }

    /** Lista las sedes del tenant; con {@code status} devuelve solo las de ese estado. */
    @Transactional(readOnly = true)
    public Page<BranchResponse> list(RequestContext context, BranchStatus status, Pageable pageable) {
        Page<Branch> page = status == null
                ? branches.findByOrganizationPublicId(context.organizationId(), pageable)
                : branches.findByOrganizationPublicIdAndStatus(context.organizationId(), status, pageable);
        return page.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BranchResponse get(RequestContext context, UUID branchId) {
        return mapper.toResponse(require(context.organizationId(), branchId));
    }

    /** Proyeccion minima para consumo interno de otro microservicio. */
    @Transactional(readOnly = true)
    public BranchRef getRef(UUID organizationId, UUID branchId) {
        return mapper.toRef(require(organizationId, branchId));
    }

    @Transactional
    public BranchResponse update(RequestContext context, UUID branchId, UpdateBranchRequest request) {
        Branch branch = require(context.organizationId(), branchId);
        branch.updateDetails(request.name(), request.address(), request.city(), request.department(),
                request.phoneE164(), request.latitude(), request.longitude(), request.openingHours());
        return mapper.toResponse(branch);
    }

    /**
     * Activa o desactiva una sede.
     *
     * <p>Desactivar libera cuota; reactivar la vuelve a reservar, de modo que el contador refleja
     * siempre las sedes realmente operativas.
     */
    @Transactional
    public BranchResponse changeStatus(RequestContext context, UUID branchId, ChangeBranchStatusRequest request) {
        Branch branch = require(context.organizationId(), branchId);
        BranchStatus target = request.status();
        if (branch.getStatus() != target) {
            if (target == BranchStatus.ACTIVE) {
                planUsage.reserve(context.organizationId(), UsageMetric.BRANCHES, 1);
            } else {
                planUsage.release(context.organizationId(), UsageMetric.BRANCHES, 1);
            }
            branch.changeStatus(target);
            log.info("Estado de sucursal actualizado organizationId={} branchId={} status={}",
                    context.organizationId(), branchId, target);
        }
        return mapper.toResponse(branch);
    }

    private Branch require(UUID organizationId, UUID branchId) {
        return branches.findByOrganizationPublicIdAndPublicId(organizationId, branchId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BRANCH_NOT_FOUND,
                        "La sucursal '" + branchId + "' no existe en esta organizacion"));
    }
}
