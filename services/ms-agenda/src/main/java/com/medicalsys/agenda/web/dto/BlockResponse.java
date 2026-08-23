package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.BlockType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BlockResponse(
        Long id,
        UUID organizationPublicId,
        UUID healthProfessionalPublicId,
        UUID physicalResourcePublicId,
        UUID branchPublicId,
        BlockType type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String reason,
        UUID createdByUserPublicId,
        OffsetDateTime createdAt) {
}
