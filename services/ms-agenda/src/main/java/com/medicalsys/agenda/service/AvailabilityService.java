package com.medicalsys.agenda.service;

import com.medicalsys.agenda.domain.AvailabilityModality;
import com.medicalsys.agenda.domain.ScheduleAvailability;
import com.medicalsys.agenda.repository.ScheduleAvailabilityRepository;
import com.medicalsys.agenda.web.dto.AvailabilityResponse;
import com.medicalsys.agenda.web.dto.CreateAvailabilityRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

    private final ScheduleAvailabilityRepository availabilities;
    private final PhysicalResourceService resourceService;
    private final SchedulingMapper mapper;

    public AvailabilityService(ScheduleAvailabilityRepository availabilities, PhysicalResourceService resourceService,
                               SchedulingMapper mapper) {
        this.availabilities = availabilities;
        this.resourceService = resourceService;
        this.mapper = mapper;
    }

    @Transactional
    public AvailabilityResponse create(CreateAvailabilityRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw ApiException.badRequest("endTime debe ser posterior a startTime");
        }
        if (request.validUntil() != null && request.validUntil().isBefore(request.validFrom())) {
            throw ApiException.badRequest("validUntil no puede ser anterior a validFrom");
        }

        ScheduleAvailability availability = new ScheduleAvailability();
        availability.setProfessionalOrganizationPublicId(request.professionalOrganizationPublicId());
        availability.setDayOfWeek(request.dayOfWeek());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());
        availability.setSlotDurationMinutes(
                request.slotDurationMinutes() == null ? 30 : request.slotDurationMinutes());
        availability.setSimultaneousCapacity(
                request.simultaneousCapacity() == null ? 1 : request.simultaneousCapacity());
        availability.setModality(request.modality() == null ? AvailabilityModality.IN_PERSON : request.modality());
        availability.setValidFrom(request.validFrom());
        availability.setValidUntil(request.validUntil());
        if (request.preferredResourcePublicId() != null) {
            availability.setPreferredResource(resourceService.require(request.preferredResourcePublicId()));
        }

        ScheduleAvailability saved = OverlapTranslator.translate(() -> availabilities.saveAndFlush(availability));
        return mapper.toAvailabilityResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listFor(UUID professionalOrganizationPublicId, boolean onlyActive) {
        return availabilities
                .findByProfessionalOrganizationPublicIdOrderByDayOfWeekAscStartTimeAsc(professionalOrganizationPublicId)
                .stream()
                .filter(availability -> !onlyActive || availability.isActive())
                .map(mapper::toAvailabilityResponse)
                .toList();
    }

    /**
     * Una disponibilidad no se borra: se desactiva. Las citas ya creadas bajo esa regla siguen
     * siendo validas y su trazabilidad se conserva.
     */
    @Transactional
    public AvailabilityResponse deactivate(Long id) {
        ScheduleAvailability availability = availabilities.findById(id)
                .orElseThrow(() -> ApiException.notFound("No existe la disponibilidad " + id));
        availability.setActive(false);
        return mapper.toAvailabilityResponse(availability);
    }
}
