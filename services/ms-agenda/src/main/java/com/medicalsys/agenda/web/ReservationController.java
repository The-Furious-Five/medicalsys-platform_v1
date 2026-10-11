package com.medicalsys.agenda.web;

import com.medicalsys.agenda.config.RequestContextHolder;
import com.medicalsys.agenda.domain.ReservationStatus;
import com.medicalsys.agenda.security.RequiresPermission;
import com.medicalsys.agenda.service.ReservationService;
import com.medicalsys.agenda.web.dto.CreateReservationRequest;
import com.medicalsys.agenda.web.dto.ReservationResponse;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scheduling/reservations")
public class ReservationController {

    private final ReservationService reservations;

    public ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("scheduling.reservation.create")
    public ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        return reservations.create(request);
    }

    @GetMapping
    @RequiresPermission("scheduling.reservation.read")
    public List<ReservationResponse> search(
            @RequestParam(required = false) UUID physicalResourcePublicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID organizationPublicId = RequestContextHolder.require().organizationId();
        return reservations.search(organizationPublicId, physicalResourcePublicId, from, to);
    }

    @PostMapping("/{id}/release")
    @RequiresPermission("scheduling.reservation.release")
    public ReservationResponse release(@PathVariable Long id) {
        return reservations.changeStatus(RequestContextHolder.require().organizationId(), id, ReservationStatus.RELEASED);
    }

    @PostMapping("/{id}/in-use")
    @RequiresPermission("scheduling.reservation.mark_in_use")
    public ReservationResponse markInUse(@PathVariable Long id) {
        return reservations.changeStatus(RequestContextHolder.require().organizationId(), id, ReservationStatus.IN_USE);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("scheduling.reservation.cancel")
    public ReservationResponse cancel(@PathVariable Long id) {
        return reservations.changeStatus(RequestContextHolder.require().organizationId(), id, ReservationStatus.CANCELLED);
    }
}
