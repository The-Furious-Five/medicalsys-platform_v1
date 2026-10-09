package com.medicalsys.tenencia.repository;

import com.medicalsys.tenencia.domain.enums.BranchStatus;
import com.medicalsys.tenencia.domain.model.Branch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a las sedes.
 *
 * <p>Toda consulta esta acotada por {@code organization.publicId}: la sucursal de otra clinica no
 * debe ser alcanzable ni siquiera conociendo su UUID.
 */
public interface BranchRepository extends JpaRepository<Branch, Long> {

    Optional<Branch> findByOrganizationPublicIdAndPublicId(UUID organizationId, UUID publicId);

    Page<Branch> findByOrganizationPublicId(UUID organizationId, Pageable pageable);

    Page<Branch> findByOrganizationPublicIdAndStatus(UUID organizationId, BranchStatus status, Pageable pageable);

    List<Branch> findByOrganizationPublicIdAndStatusOrderByCodeAsc(UUID organizationId, BranchStatus status);

    boolean existsByOrganizationPublicIdAndCode(UUID organizationId, String code);

    Optional<Branch> findByOrganizationPublicIdAndHeadquartersTrue(UUID organizationId);

    /** Sucursales existentes del tenant; es el valor de referencia de la cuota BRANCHES. */
    long countByOrganizationPublicId(UUID organizationId);
}
