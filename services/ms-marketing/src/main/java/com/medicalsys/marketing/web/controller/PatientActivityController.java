package com.medicalsys.marketing.web.controller;

import com.medicalsys.marketing.common.security.RequiresPermission;
import com.medicalsys.marketing.common.tenant.RequestContextHolder;
import com.medicalsys.marketing.service.PatientActivityService;
import com.medicalsys.marketing.web.dto.OptOutRequest;
import com.medicalsys.marketing.web.dto.PageResponse;
import com.medicalsys.marketing.web.dto.PatientActivityResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Actividad comercial del paciente y exclusion de comunicaciones.
 *
 * <p>Vale la pena leer {@code PatientActivityResponse} entero antes de usar esta ruta: hay fechas,
 * contadores e importes, y no hay un solo campo que diga por que el paciente vino. Es toda la
 * historia que marketing conoce.
 *
 * <p>El opt-out admite el rol PATIENT porque el portal del paciente tiene que poder ejercerlo, y
 * debe seguir funcionando aunque ms-pacientes este caido: el servicio que manda la publicidad es
 * este.
 */
@RestController
@RequestMapping("/api/v1/pacientes")
public class PatientActivityController {

    private final PatientActivityService service;

    public PatientActivityController(PatientActivityService service) {
        this.service = service;
    }

    /** Lista la actividad comercial registrada por el tenant. */
    @GetMapping("/actividad")
    @RequiresPermission("marketing.patient_activity.read")
    public PageResponse<PatientActivityResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.list(RequestContextHolder.require(), pageable));
    }

    /** Proyeccion comercial de un paciente concreto. */
    @GetMapping("/{patientId}/actividad")
    @RequiresPermission("marketing.patient_activity.read")
    public PatientActivityResponse get(@PathVariable UUID patientId) {
        return service.get(RequestContextHolder.require(), patientId);
    }

    /** Registra o revoca la exclusion comercial del paciente. */
    @PutMapping("/{patientId}/opt-out")
    @RequiresPermission("marketing.opt_out.update")
    public ResponseEntity<PatientActivityResponse> setOptOut(@PathVariable UUID patientId,
            @Valid @RequestBody OptOutRequest request) {
        return ResponseEntity.ok(service.setOptOut(RequestContextHolder.require(), patientId,
                request.optOut()));
    }
}
