package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.model.AudienceSegment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los segmentos de audiencia, siempre acotado al tenant. */
public interface AudienceSegmentRepository extends JpaRepository<AudienceSegment, Long> {

    Optional<AudienceSegment> findByPublicIdAndOrganizationPublicId(UUID publicId, UUID organizationPublicId);

    Page<AudienceSegment> findByOrganizationPublicId(UUID organizationPublicId, Pageable pageable);

    Page<AudienceSegment> findByOrganizationPublicIdAndActive(UUID organizationPublicId, boolean active,
            Pageable pageable);

    boolean existsByOrganizationPublicIdAndName(UUID organizationPublicId, String name);
}
