package com.medicalsys.profesionales.web;

import com.medicalsys.profesionales.domain.*;
import java.util.List;
import java.util.UUID;

public record ProfessionalResponse(UUID id, String fullName, String licenseNumber,
                                   ProfessionalType professionalType, ProfessionalStatus status,
                                   List<SpecialtyResponse> specialties, List<OrganizationResponse> organizations) {
    public record SpecialtyResponse(String code, boolean primary, Short graduationYear) { }
    public record OrganizationResponse(UUID id, UUID organizationId, UUID branchId,
                                       OrganizationModality modality, OrganizationStatus status) { }
}
