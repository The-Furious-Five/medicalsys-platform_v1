package com.medicalsys.pacientes.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PatientMergeResponse(
        UUID survivingPatientId,
        UUID mergedPatientId,
        String reason,
        UUID mergedByUserId,
        OffsetDateTime mergedAt,
        UUID eventId) {
}
