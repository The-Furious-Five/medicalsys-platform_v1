package com.medicalsys.pacientes.web.controller;

import com.medicalsys.pacientes.common.security.RequiresPermission;
import com.medicalsys.pacientes.common.tenant.RequestContextHolder;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.service.PatientAccessService;
import com.medicalsys.pacientes.service.PatientService;
import com.medicalsys.pacientes.web.dto.ConfirmPatientAccessRequest;
import com.medicalsys.pacientes.web.dto.PatientAccessGrantResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solicitar, confirmar y revocar el permiso de un tenant para ver la ficha completa y el historial
 * clínico de un paciente registrado por otro tenant. Lo opera personal de la clínica, pero solo el
 * paciente puede autorizarlo (confirma el código OTP que le llega a su WhatsApp) — no existe hoy un
 * autoservicio del paciente, así que quien aprieta los botones es recepción/médico, no el paciente.
 */
@Validated
@RestController
@RequestMapping("/api/v1/patients/{patientId}/acceso")
public class PatientAccessController {

    private final PatientService patients;
    private final PatientAccessService access;

    public PatientAccessController(PatientService patients, PatientAccessService access) {
        this.patients = patients;
        this.access = access;
    }

    @PostMapping("/solicitudes")
    @RequiresPermission("patients.access_grant.request")
    public ResponseEntity<PatientAccessGrantResponse> requestAccess(@PathVariable UUID patientId) {
        Patient patient = patients.requireEntity(patientId);
        PatientAccessGrantResponse response = access.requestAccess(patient,
                RequestContextHolder.require().organizationId(), RequestContextHolder.require().userId());
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/solicitudes/{grantId}/confirmacion")
    @RequiresPermission("patients.access_grant.confirm")
    public PatientAccessGrantResponse confirmAccess(@PathVariable UUID patientId, @PathVariable UUID grantId,
            @Valid @RequestBody ConfirmPatientAccessRequest request) {
        Patient patient = patients.requireEntity(patientId);
        return access.confirmAccess(patient, grantId, request.otpCode());
    }

    @PostMapping("/solicitudes/{grantId}/revocacion")
    @RequiresPermission("patients.access_grant.revoke")
    public PatientAccessGrantResponse revoke(@PathVariable UUID patientId, @PathVariable UUID grantId) {
        Patient patient = patients.requireEntity(patientId);
        return access.revoke(patient, grantId, RequestContextHolder.require().organizationId());
    }

    @GetMapping("/solicitudes")
    @RequiresPermission("patients.access_grant.read")
    public List<PatientAccessGrantResponse> list(@PathVariable UUID patientId) {
        Patient patient = patients.requireEntity(patientId);
        return access.list(patient, RequestContextHolder.require().organizationId());
    }
}
