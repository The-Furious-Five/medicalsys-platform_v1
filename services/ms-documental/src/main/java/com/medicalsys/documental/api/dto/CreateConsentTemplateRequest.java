package com.medicalsys.documental.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CreateConsentTemplateRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 20) String specialtyCode,
        @NotBlank String markdownBody,
        List<@Size(max = 60) String> variables,
        boolean requiresWitness,
        boolean requiresGuardianForMinor,
        @NotNull LocalDate validFrom,
        LocalDate validUntil) {
}
