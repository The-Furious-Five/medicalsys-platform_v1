package com.medicalsys.documental.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ConsentTemplateResponse(
        UUID templateId,
        UUID organizationId,
        String code,
        int version,
        String title,
        String specialtyCode,
        String markdownBody,
        List<String> variables,
        boolean requiresWitness,
        boolean requiresGuardianForMinor,
        LocalDate validFrom,
        LocalDate validUntil,
        String status,
        Instant createdAt) {
}
