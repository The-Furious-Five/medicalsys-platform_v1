package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.model.LoyaltyProgram;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los programas de fidelizacion del tenant. */
public interface LoyaltyProgramRepository extends JpaRepository<LoyaltyProgram, Long> {

    Optional<LoyaltyProgram> findByPublicIdAndOrganizationPublicId(UUID publicId, UUID organizationPublicId);

    List<LoyaltyProgram> findByOrganizationPublicIdOrderByCreatedAtAsc(UUID organizationPublicId);

    /**
     * Programa activo del tenant.
     *
     * <p>El esquema admite varios programas por organizacion y el MVP usa el primero activo. La
     * acumulacion automatica necesita elegir uno sin ambiguedad, y este es el criterio declarado.
     */
    Optional<LoyaltyProgram> findFirstByOrganizationPublicIdAndActiveOrderByCreatedAtAsc(
            UUID organizationPublicId, boolean active);

    boolean existsByOrganizationPublicIdAndName(UUID organizationPublicId, String name);
}
