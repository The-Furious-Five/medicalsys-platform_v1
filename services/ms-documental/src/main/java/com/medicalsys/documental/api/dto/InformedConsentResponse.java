package com.medicalsys.documental.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InformedConsentResponse(
        UUID consentId,
        UUID organizationId,
        String templateCode,
        int templateVersion,
        UUID patientId,
        UUID medicalRecordId,
        UUID appointmentId,
        UUID clinicalEncounterId,
        UUID healthProfessionalId,
        UUID serviceOfferingId,
        String status,
        String signatureMethod,
        String signerType,
        String signerDocumentNumber,
        UUID pdfDocumentId,
        Instant generatedAt,
        Instant signedAt,
        Instant revokedAt,
        String revocationReason,
        List<ConsentEventResponse> events) {
}
