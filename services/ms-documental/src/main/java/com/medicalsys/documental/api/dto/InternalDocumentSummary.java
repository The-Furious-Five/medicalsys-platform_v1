package com.medicalsys.documental.api.dto;

import java.util.UUID;

public record InternalDocumentSummary(
        UUID documentId,
        UUID patientId,
        String type,
        String mimeType,
        long sizeBytes,
        String sha256Hash,
        String status) {
}
