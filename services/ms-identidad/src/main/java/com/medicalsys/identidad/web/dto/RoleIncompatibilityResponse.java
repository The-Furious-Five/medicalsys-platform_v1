package com.medicalsys.identidad.web.dto;

import java.time.Instant;
import java.util.UUID;

public record RoleIncompatibilityResponse(
        String roleA,
        String roleB,
        String reason,
        UUID createdByPublicId,
        Instant createdAt) {
}
