package com.medicalsys.pacientes.web.controller;

import com.medicalsys.pacientes.common.error.ApiException;
import com.medicalsys.pacientes.common.error.ErrorCode;
import com.medicalsys.pacientes.common.security.RequiresPermission;
import com.medicalsys.pacientes.common.tenant.RequestContextHolder;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.repository.PatientRepository;
import com.medicalsys.pacientes.service.PatientAccessService;
import com.medicalsys.pacientes.web.dto.ConfirmPatientAccessRequest;
import com.medicalsys.pacientes.web.dto.PatientAccessGrantResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El identificador de paciente se obtiene de la cuenta del JWT, nunca del cliente. */
@RestController
@RequestMapping("/api/v1/patients/me/access-grants")
public class SelfPatientAccessController {
    private final PatientRepository patients;
    private final PatientAccessService access;

    public SelfPatientAccessController(PatientRepository patients, PatientAccessService access) {
        this.patients = patients;
        this.access = access;
    }

    @GetMapping
    @RequiresPermission("patients.self_access.read")
    public List<PatientAccessGrantResponse> list() {
        return access.listForOwner(owner());
    }

    @PostMapping("/{grantId}/confirmacion")
    @RequiresPermission("patients.self_access.confirm")
    public PatientAccessGrantResponse confirm(@PathVariable UUID grantId,
            @Valid @RequestBody ConfirmPatientAccessRequest request) {
        return access.confirmAccess(owner(), grantId, request.otpCode());
    }

    @PostMapping("/{grantId}/revocacion")
    @RequiresPermission("patients.self_access.revoke")
    public PatientAccessGrantResponse revoke(@PathVariable UUID grantId) {
        return access.revokeForOwner(owner(), grantId);
    }

    private Patient owner() {
        return patients.findByUserAccountPublicId(RequestContextHolder.require().userId())
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_GRANT_FORBIDDEN,
                        "Su cuenta no esta vinculada a una ficha de paciente"));
    }
}
