package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.AppointmentStatus;
import com.medicalsys.agenda.domain.StatusChangeOrigin;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentHistoryResponse(
        Long id,
        AppointmentStatus previousStatus,
        AppointmentStatus newStatus,
        String reason,
        UUID userAccountPublicId,
        StatusChangeOrigin origin,
        OffsetDateTime occurredAt) {
}
