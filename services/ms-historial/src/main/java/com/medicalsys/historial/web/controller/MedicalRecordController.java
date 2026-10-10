package com.medicalsys.historial.web.controller;

import com.medicalsys.historial.common.security.RequiresPermission;
import com.medicalsys.historial.common.tenant.RequestContext;
import com.medicalsys.historial.common.tenant.RequestContextHolder;
import com.medicalsys.historial.service.MedicalRecordService;
import com.medicalsys.historial.service.MedicalRecordService.OpenResult;
import com.medicalsys.historial.web.dto.ChangeRecordStatusRequest;
import com.medicalsys.historial.web.dto.MedicalRecordResponse;
import com.medicalsys.historial.web.dto.OpenMedicalRecordRequest;
import com.medicalsys.historial.web.dto.PageResponse;
import com.medicalsys.historial.web.dto.UpdateSharingRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expedientes clinicos.
 *
 * <p>La apertura es idempotente: la primera solicitud devuelve 201 y las siguientes 200 con el
 * mismo expediente.
 */
@RestController
@RequestMapping("/api/v1/expedientes")
public class MedicalRecordController {

    private final MedicalRecordService service;

    public MedicalRecordController(MedicalRecordService service) {
        this.service = service;
    }

    /** Crea el expediente del paciente o devuelve el existente. */
    @PostMapping
    @RequiresPermission("clinical.record.create")
    public ResponseEntity<MedicalRecordResponse> open(@Valid @RequestBody OpenMedicalRecordRequest request) {
        OpenResult result = service.openOrGet(RequestContextHolder.require(), request);
        URI location = URI.create("/api/v1/expedientes/" + result.record().id());
        return result.created()
                ? ResponseEntity.created(location).body(result.record())
                : ResponseEntity.ok().location(location).body(result.record());
    }

    @GetMapping("/{recordId}")
    @RequiresPermission("clinical.record.read")
    public MedicalRecordResponse get(@PathVariable UUID recordId) {
        return service.getByPublicId(RequestContextHolder.require(), recordId);
    }

    /** Lista los expedientes del tenant o resuelve el de un paciente concreto. */
    @GetMapping
    @RequiresPermission("clinical.record.read")
    public Object list(@RequestParam(required = false) UUID patientId,
            @PageableDefault(size = 20, sort = "openedOn", direction = Sort.Direction.DESC) Pageable pageable) {
        RequestContext context = RequestContextHolder.require();
        return patientId != null
                ? service.getByPatient(context, patientId)
                : PageResponse.of(service.list(context, pageable));
    }

    /** Registra o revoca la autorizacion del paciente para compartir su historial. */
    @PatchMapping("/{recordId}/compartir")
    @RequiresPermission("clinical.record.share")
    public MedicalRecordResponse updateSharing(@PathVariable UUID recordId,
            @Valid @RequestBody UpdateSharingRequest request) {
        return service.updateSharing(RequestContextHolder.require(), recordId, request);
    }

    /** Cambia el estado administrativo del expediente. */
    @PatchMapping("/{recordId}/estado")
    @RequiresPermission("clinical.record.change_status")
    public MedicalRecordResponse changeStatus(@PathVariable UUID recordId,
            @Valid @RequestBody ChangeRecordStatusRequest request) {
        return service.changeStatus(RequestContextHolder.require(), recordId, request);
    }
}
