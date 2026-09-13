package com.medicalsys.documental.api.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentLinkResponse(
        String entityType,
        UUID entityId,
        String description,
        UUID linkedByUserId,
        Instant createdAt) {
}
