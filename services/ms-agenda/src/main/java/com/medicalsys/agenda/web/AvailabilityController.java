package com.medicalsys.agenda.web;

import com.medicalsys.agenda.config.RequestContextHolder;
import com.medicalsys.agenda.security.RequiresPermission;
import com.medicalsys.agenda.service.AvailabilityService;
import com.medicalsys.agenda.service.SlotService;
import com.medicalsys.agenda.web.dto.AvailabilityResponse;
import com.medicalsys.agenda.web.dto.CreateAvailabilityRequest;
import com.medicalsys.agenda.web.dto.DaySlotsResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/scheduling")
public class AvailabilityController {

    private final AvailabilityService availabilities;
    private final SlotService slots;

    public AvailabilityController(AvailabilityService availabilities, SlotService slots) {
        this.availabilities = availabilities;
        this.slots = slots;
    }

    @PostMapping("/availabilities")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("scheduling.availability.create")
    public AvailabilityResponse create(@Valid @RequestBody CreateAvailabilityRequest request) {
        return availabilities.create(request);
    }

    @GetMapping("/availabilities")
    @RequiresPermission("scheduling.availability.read")
    public List<AvailabilityResponse> list(
            @RequestParam UUID professionalOrganizationPublicId,
            @RequestParam(defaultValue = "true") boolean onlyActive) {
        return availabilities.listFor(professionalOrganizationPublicId, onlyActive);
    }

    @DeleteMapping("/availabilities/{id}")
    @RequiresPermission("scheduling.availability.deactivate")
    public AvailabilityResponse deactivate(@PathVariable Long id) {
        return availabilities.deactivate(id);
    }

    @GetMapping("/availability/slots")
    @RequiresPermission("scheduling.availability.read")
    public DaySlotsResponse freeSlots(
            @RequestParam UUID professionalOrganizationPublicId,
            @RequestParam UUID healthProfessionalPublicId,
            @RequestParam(required = false) UUID branchPublicId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID organizationPublicId = RequestContextHolder.require().organizationId();
        return slots.freeSlots(organizationPublicId, professionalOrganizationPublicId, healthProfessionalPublicId,
                branchPublicId, date);
    }
}
