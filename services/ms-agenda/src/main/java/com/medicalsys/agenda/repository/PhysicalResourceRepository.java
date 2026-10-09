package com.medicalsys.agenda.repository;

import com.medicalsys.agenda.domain.PhysicalResource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PhysicalResourceRepository
        extends JpaRepository<PhysicalResource, Long>, JpaSpecificationExecutor<PhysicalResource> {

    Optional<PhysicalResource> findByPublicId(UUID publicId);

    Optional<PhysicalResource> findByOrganizationPublicIdAndPublicId(UUID organizationPublicId, UUID publicId);

    boolean existsByOrganizationPublicIdAndCodeIgnoreCase(UUID organizationPublicId, String code);
}
