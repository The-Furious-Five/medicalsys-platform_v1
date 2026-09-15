package com.medicalsys.documental.repository;

import com.medicalsys.documental.domain.InformedConsent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InformedConsentRepository extends JpaRepository<InformedConsent, Long> {

    @EntityGraph(attributePaths = {"events", "template", "pdfDocument"})
    @Query("""
            select c from InformedConsent c
            where c.publicId = :publicId and c.organizationPublicId = :organizationId
            """)
    Optional<InformedConsent> findDetailedByPublicIdAndOrganization(
            @Param("publicId") UUID publicId, @Param("organizationId") UUID organizationId);

    List<InformedConsent> findAllByOrganizationPublicIdAndPatientPublicIdOrderByGeneratedAtDesc(
            UUID organizationPublicId, UUID patientPublicId);
}
