package com.medicalsys.documental.api.dto;

import com.medicalsys.documental.service.IntegrityCheckResult;
import java.util.UUID;

public record DocumentIntegrityResponse(
        UUID documentId,
        String expectedHash,
        String actualHash,
        boolean matches) {

    public static DocumentIntegrityResponse from(IntegrityCheckResult result) {
        return new DocumentIntegrityResponse(
                result.documentId(), result.expectedHash(), result.actualHash(), result.matches());
    }
}
