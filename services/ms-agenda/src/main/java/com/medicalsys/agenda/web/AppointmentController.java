package com.medicalsys.agenda.web;

import com.medicalsys.agenda.config.RequestContextHolder;
import com.medicalsys.agenda.domain.AppointmentStatus;
import com.medicalsys.agenda.security.RequiresPermission;
import com.medicalsys.agenda.service.AppointmentService;
import com.medicalsys.agenda.web.dto.AppointmentHistoryResponse;
import com.medicalsys.agenda.web.dto.AppointmentResponse;
import com.medicalsys.agenda.web.dto.CreateAppointmentRequest;
import com.medicalsys.agenda.web.dto.PageResponse;
import com.medicalsys.agenda.web.dto.RescheduleAppointmentRequest;
import com.medicalsys.agenda.web.dto.StatusChangeRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scheduling/appointments")
public class AppointmentController {

    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @PostMapping
    @RequiresPermission("scheduling.appointment.create")
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentResponse created = appointments.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/scheduling/appointments/" + created.publicId()))
                .body(created);
    }

    @GetMapping
    @RequiresPermission("scheduling.appointment.read")
    public PageResponse<AppointmentResponse> search(
            @RequestParam(required = false) UUID branchPublicId,
            @RequestParam(required = false) UUID healthProfessionalPublicId,
            @RequestParam(required = false) UUID patientPublicId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID organizationPublicId = RequestContextHolder.require().organizationId();
        return appointments.search(organizationPublicId, branchPublicId, healthProfessionalPublicId, patientPublicId,
                status, from, to, PageRequest.of(page, Math.min(size, 100), Sort.by("startsAt").ascending()));
    }

    @GetMapping("/{publicId}")
    @RequiresPermission("scheduling.appointment.read")
    public AppointmentResponse get(@PathVariable UUID publicId) {
        return appointments.get(RequestContextHolder.require().organizationId(), publicId);
    }

    @GetMapping("/{publicId}/history")
    @RequiresPermission("scheduling.appointment.read")
    public List<AppointmentHistoryResponse> history(@PathVariable UUID publicId) {
        return appointments.history(RequestContextHolder.require().organizationId(), publicId);
    }

    @PostMapping("/{publicId}/confirm")
    @RequiresPermission("scheduling.appointment.confirm")
    public AppointmentResponse confirm(@PathVariable UUID publicId,
                                       @RequestBody(required = false) StatusChangeRequest request) {
        return appointments.changeStatus(RequestContextHolder.require().organizationId(), publicId,
                AppointmentStatus.CONFIRMED, request);
    }

    @PostMapping("/{publicId}/check-in")
    @RequiresPermission("scheduling.appointment.check_in")
    public AppointmentResponse checkIn(@PathVariable UUID publicId,
                                       @RequestBody(required = false) StatusChangeRequest request) {
        return appointments.changeStatus(RequestContextHolder.require().organizationId(), publicId,
                AppointmentStatus.WAITING, request);
    }

    @PostMapping("/{publicId}/start")
    @RequiresPermission("scheduling.appointment.attend")
    public AppointmentResponse start(@PathVariable UUID publicId,
                                     @RequestBody(required = false) StatusChangeRequest request) {
        return appointments.changeStatus(RequestContextHolder.require().organizationId(), publicId,
                AppointmentStatus.IN_PROGRESS, request);
    }

    @PostMapping("/{publicId}/complete")
    @RequiresPermission("scheduling.appointment.attend")
    public AppointmentResponse complete(@PathVariable UUID publicId,
                                        @RequestBody(required = false) StatusChangeRequest request) {
        return appointments.changeStatus(RequestContextHolder.require().organizationId(), publicId,
                AppointmentStatus.ATTENDED, request);
    }

    @PostMapping("/{publicId}/no-show")
    @RequiresPermission("scheduling.appointment.no_show")
    public AppointmentResponse noShow(@PathVariable UUID publicId,
                                      @RequestBody(required = false) StatusChangeRequest request) {
        return appointments.changeStatus(RequestContextHolder.require().organizationId(), publicId,
                AppointmentStatus.NO_SHOW, request);
    }

    @PostMapping("/{publicId}/cancel")
    @RequiresPermission("scheduling.appointment.cancel")
    public AppointmentResponse cancel(@PathVariable UUID publicId,
                                      @RequestBody(required = false) StatusChangeRequest request) {
        return appointments.changeStatus(RequestContextHolder.require().organizationId(), publicId,
                AppointmentStatus.CANCELLED, request);
    }

    @PostMapping("/{publicId}/reschedule")
    @RequiresPermission("scheduling.appointment.reschedule")
    public ResponseEntity<AppointmentResponse> reschedule(@PathVariable UUID publicId,
                                                          @Valid @RequestBody RescheduleAppointmentRequest request) {
        AppointmentResponse created = appointments.reschedule(RequestContextHolder.require().organizationId(),
                publicId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/scheduling/appointments/" + created.publicId()))
                .body(created);
    }
}
