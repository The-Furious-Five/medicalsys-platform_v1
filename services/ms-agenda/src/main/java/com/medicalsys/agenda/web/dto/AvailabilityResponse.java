package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.AvailabilityModality;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityResponse(
        Long id,
        UUID professionalOrganizationPublicId,
        Short dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Short slotDurationMinutes,
        Short simultaneousCapacity,
        AvailabilityModality modality,
        UUID preferredResourcePublicId,
        LocalDate validFrom,
        LocalDate validUntil,
        boolean active) {
}
