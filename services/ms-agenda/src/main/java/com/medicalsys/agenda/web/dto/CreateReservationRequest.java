package com.medicalsys.agenda.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateReservationRequest(
        @NotNull UUID organizationPublicId,
        @NotNull UUID physicalResourcePublicId,
        UUID appointmentPublicId,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        @Size(max = 200) String purpose,
        UUID createdByUserPublicId) {
}
