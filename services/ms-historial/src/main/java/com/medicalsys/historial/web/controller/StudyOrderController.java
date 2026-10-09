package com.medicalsys.historial.web.controller;

import com.medicalsys.historial.common.security.RequiresRole;
import com.medicalsys.historial.common.security.Role;
import com.medicalsys.historial.common.tenant.RequestContextHolder;
import com.medicalsys.historial.domain.enums.StudyOrderStatus;
import com.medicalsys.historial.service.StudyOrderService;
import com.medicalsys.historial.web.dto.ChangeStudyOrderStatusRequest;
import com.medicalsys.historial.web.dto.CreateStudyOrderRequest;
import com.medicalsys.historial.web.dto.PageResponse;
import com.medicalsys.historial.web.dto.RegisterStudyResultRequest;
import com.medicalsys.historial.web.dto.ScheduleStudyOrderRequest;
import com.medicalsys.historial.web.dto.StudyOrderResponse;
import com.medicalsys.historial.web.dto.StudyResultResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ordenes de estudio y sus resultados.
 *
 * <p>El {@code id} de la orden es el orderId que consumen ms-agenda y ms-documental. Al registrar un
 * resultado la orden queda informada; el archivo permanece en ms-documental y aqui solo vive su
 * referencia.
 */
@RestController
@RequestMapping("/api/v1")
public class StudyOrderController {

    private final StudyOrderService service;

    public StudyOrderController(StudyOrderService service) {
        this.service = service;
    }

    @PostMapping("/encuentros/{encounterId}/ordenes-estudio")
    @RequiresRole({Role.PHYSICIAN, Role.PLATFORM_ADMIN})
    public ResponseEntity<StudyOrderResponse> create(@PathVariable UUID encounterId,
            @Valid @RequestBody CreateStudyOrderRequest request) {
        StudyOrderResponse body = service.create(RequestContextHolder.require(), encounterId, request);
        return ResponseEntity.created(URI.create("/api/v1/ordenes-estudio/" + body.id())).body(body);
    }

    @GetMapping("/encuentros/{encounterId}/ordenes-estudio")
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public List<StudyOrderResponse> listByEncounter(@PathVariable UUID encounterId) {
        return service.listByEncounter(RequestContextHolder.require(), encounterId);
    }

    @GetMapping("/ordenes-estudio/{orderId}")
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public StudyOrderResponse get(@PathVariable UUID orderId) {
        return service.get(RequestContextHolder.require(), orderId);
    }

    /** Busqueda paginada por estado, tipo de estudio y paciente. */
    @GetMapping("/ordenes-estudio")
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public PageResponse<StudyOrderResponse> search(
            @RequestParam(required = false) StudyOrderStatus status,
            @RequestParam(required = false) String studyTypeCode,
            @RequestParam(required = false) UUID patientId,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.search(RequestContextHolder.require(), status, studyTypeCode, patientId,
                pageable));
    }

    /** Vincula la cita de toma creada por ms-agenda. */
    @PatchMapping("/ordenes-estudio/{orderId}/cita-toma")
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public StudyOrderResponse scheduleCollection(@PathVariable UUID orderId,
            @Valid @RequestBody ScheduleStudyOrderRequest request) {
        return service.scheduleCollection(RequestContextHolder.require(), orderId, request);
    }

    @PatchMapping("/ordenes-estudio/{orderId}/estado")
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public StudyOrderResponse changeStatus(@PathVariable UUID orderId,
            @Valid @RequestBody ChangeStudyOrderStatusRequest request) {
        return service.changeStatus(RequestContextHolder.require(), orderId, request);
    }

    /** Registra el resultado del estudio con su referencia documental. */
    @PostMapping("/ordenes-estudio/{orderId}/resultados")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public StudyResultResponse registerResult(@PathVariable UUID orderId,
            @Valid @RequestBody RegisterStudyResultRequest request) {
        return service.registerResult(RequestContextHolder.require(), orderId, request);
    }

    @GetMapping("/ordenes-estudio/{orderId}/resultados")
    @RequiresRole({Role.PHYSICIAN, Role.RECEPTIONIST, Role.ADMINISTRATOR, Role.PLATFORM_ADMIN})
    public List<StudyResultResponse> listResults(@PathVariable UUID orderId) {
        return service.listResults(RequestContextHolder.require(), orderId);
    }
}
