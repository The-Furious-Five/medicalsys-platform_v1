package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.model.RefOrganization;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la replica de organizaciones. */
public interface RefOrganizationRepository extends JpaRepository<RefOrganization, UUID> {
}
