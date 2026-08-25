package com.medicalsys.profesionales.web;

import com.medicalsys.profesionales.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateProfessionalRequest(
        @NotNull UUID userAccountId,
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 20) String documentNumber,
        @Size(max = 5) String documentComplement,
        @NotBlank @Size(max = 120) String givenNames,
        @NotBlank @Size(max = 80) String paternalSurname,
        @Size(max = 80) String maternalSurname,
        @NotNull ProfessionalType professionalType,
        @NotBlank @Size(max = 40) String professionalLicenseNumber,
        @Size(max = 160) String title,
        @Size(max = 1000) String biography,
        @NotEmpty List<@Valid SpecialtyRequest> specialties,
        @NotNull UUID organizationId,
        UUID branchId,
        OrganizationModality modality,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionPercentage,
        @NotNull LocalDate startDate) {
    public record SpecialtyRequest(
            @NotBlank @Size(max = 20) String code,
            boolean primary,
            @Min(1900) @Max(2200) Short graduationYear) { }
}
