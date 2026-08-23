package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.AvailabilityModality;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateAvailabilityRequest(
        @NotNull UUID professionalOrganizationPublicId,
        @NotNull @Min(1) @Max(7) Short dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(5) @Max(240) Short slotDurationMinutes,
        @Min(1) Short simultaneousCapacity,
        AvailabilityModality modality,
        UUID preferredResourcePublicId,
        @NotNull LocalDate validFrom,
        LocalDate validUntil) {
}
