package com.medicalsys.documental.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record GenerateInformedConsentRequest(
        @NotBlank String templateCode,
        @NotNull UUID patientId,
        @NotNull UUID medicalRecordId,
        UUID appointmentId,
        UUID clinicalEncounterId,
        @NotNull UUID healthProfessionalId,
        UUID serviceOfferingId,
        Map<String, Object> variables) {
}
