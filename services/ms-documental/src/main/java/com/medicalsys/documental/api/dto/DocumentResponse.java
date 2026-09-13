package com.medicalsys.documental.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DocumentResponse(
        UUID documentId,
        UUID patientId,
        String type,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String sha256Hash,
        boolean dicom,
        Map<String, Object> metadata,
        String status,
        String antivirusStatus,
        UUID uploadedByUserId,
        Instant uploadedAt,
        LocalDate retentionUntil,
        List<DocumentLinkResponse> links) {
}
