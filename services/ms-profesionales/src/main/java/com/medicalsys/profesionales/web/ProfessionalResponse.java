package com.medicalsys.profesionales.web;

import com.medicalsys.profesionales.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProfessionalResponse(UUID id, String fullName, String licenseNumber,
                                   ProfessionalType professionalType, ProfessionalStatus status,
                                   List<SpecialtyResponse> specialties, List<OrganizationResponse> organizations) {
    public record SpecialtyResponse(String code, boolean primary, Short graduationYear) { }

    /**
     * El vinculo con un centro. `id` es el `professionalOrganizationPublicId` que ms-agenda
     * exige para calcular disponibilidad, por eso viaja siempre.
     *
     * <p>La comision y la fecha de inicio se exponen porque el alta ya las acepta y la entidad
     * las guarda: sin ellas la ficha del profesional mostraba dos columnas permanentemente
     * vacias sobre datos que si existian en la base.
     */
    public record OrganizationResponse(UUID id, UUID organizationId, UUID branchId,
                                       OrganizationModality modality, OrganizationStatus status,
                                       BigDecimal commissionPercentage, LocalDate startDate,
                                       LocalDate endDate) { }
}
