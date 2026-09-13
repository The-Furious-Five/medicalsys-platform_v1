package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.enums.CampaignStatus;
import com.medicalsys.marketing.domain.model.Campaign;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las campanas del tenant. */
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    Optional<Campaign> findByPublicIdAndOrganizationPublicId(UUID publicId, UUID organizationPublicId);

    /** Busqueda sin tenant: solo para el consumo de eventos, que ya trae su propia organizacion. */
    Optional<Campaign> findByPublicId(UUID publicId);

    Page<Campaign> findByOrganizationPublicId(UUID organizationPublicId, Pageable pageable);

    Page<Campaign> findByOrganizationPublicIdAndStatus(UUID organizationPublicId, CampaignStatus status,
            Pageable pageable);

    long countByAudienceSegmentId(Long audienceSegmentId);
}
