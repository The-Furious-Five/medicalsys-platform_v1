package com.medicalsys.documental.api.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummaryResponse(
        UUID documentId,
        UUID patientId,
        String type,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String status,
        Instant uploadedAt) {
}
