package com.medicalsys.agenda.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RescheduleAppointmentRequest(
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        UUID healthProfessionalPublicId,
        UUID physicalResourcePublicId,
        @Size(max = 300) String reason,
        UUID userAccountPublicId) {
}
