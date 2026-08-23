package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.ReservationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationResponse(
        Long id,
        UUID organizationPublicId,
        UUID physicalResourcePublicId,
        String physicalResourceCode,
        UUID appointmentPublicId,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String purpose,
        ReservationStatus status,
        UUID createdByUserPublicId,
        OffsetDateTime createdAt) {
}
