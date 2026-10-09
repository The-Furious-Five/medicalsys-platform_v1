package com.medicalsys.profesionales.repository;

import com.medicalsys.profesionales.domain.HealthProfessional;
import com.medicalsys.profesionales.domain.OrganizationStatus;
import com.medicalsys.profesionales.domain.ProfessionalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthProfessionalRepository extends JpaRepository<HealthProfessional, Long> {
    Optional<HealthProfessional> findByPublicId(UUID publicId);

    @Query("""
            select distinct p from HealthProfessional p join p.specialties s join p.organizations o
            where o.organizationPublicId = :organizationId and o.status = :organizationStatus
              and p.status = :professionalStatus
              and (:specialtyCode is null or s.specialtyCode = :specialtyCode)
            order by p.paternalSurname, p.givenNames
            """)
    List<HealthProfessional> findActiveByOrganizationAndSpecialty(
            @Param("organizationId") UUID organizationId,
            @Param("specialtyCode") String specialtyCode,
            @Param("organizationStatus") OrganizationStatus organizationStatus,
            @Param("professionalStatus") ProfessionalStatus professionalStatus);
}
