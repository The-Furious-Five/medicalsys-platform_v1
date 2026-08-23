package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.AvailabilityModality;
import java.time.OffsetDateTime;

public record SlotResponse(
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        AvailabilityModality modality,
        int remainingCapacity) {
}
