package com.medicalsys.pacientes.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PatientAccessGrantResponse(
        UUID grantId,
        UUID patientId,
        UUID requestingOrganizationId,
        String status,
        OffsetDateTime requestedAt,
        OffsetDateTime authorizedAt,
        OffsetDateTime revokedAt) {
}
