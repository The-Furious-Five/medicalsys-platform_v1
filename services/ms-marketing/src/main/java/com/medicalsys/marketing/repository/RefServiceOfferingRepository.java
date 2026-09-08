package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.model.RefServiceOffering;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la replica del catalogo de prestaciones. */
public interface RefServiceOfferingRepository extends JpaRepository<RefServiceOffering, UUID> {

    Optional<RefServiceOffering> findByPublicIdAndOrganizationPublicId(UUID publicId, UUID organizationPublicId);

    List<RefServiceOffering> findByOrganizationPublicIdOrderByNameAsc(UUID organizationPublicId);
}
