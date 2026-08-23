package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.BlockType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateBlockRequest(
        @NotNull UUID organizationPublicId,
        UUID healthProfessionalPublicId,
        UUID physicalResourcePublicId,
        UUID branchPublicId,
        @NotNull BlockType type,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        @Size(max = 250) String reason,
        UUID createdByUserPublicId) {
}
