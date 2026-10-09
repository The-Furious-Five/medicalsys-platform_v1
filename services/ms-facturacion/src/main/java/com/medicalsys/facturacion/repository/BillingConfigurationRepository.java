package com.medicalsys.facturacion.repository;

import com.medicalsys.facturacion.domain.model.BillingConfiguration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la configuracion fiscal. Hay a lo sumo una fila por organizacion. */
public interface BillingConfigurationRepository extends JpaRepository<BillingConfiguration, Long> {

    Optional<BillingConfiguration> findByOrganizationPublicId(UUID organizationPublicId);
}
