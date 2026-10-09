package com.medicalsys.tenencia.repository;

import com.medicalsys.tenencia.domain.model.ServiceOffering;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso al catalogo de prestaciones del tenant.
 *
 * <p>La busqueda por {@code publicId} incluye siempre la organizacion, incluso en la ruta interna
 * que consume ms-facturacion: un servicio vecino tampoco puede leer el precio de otra clinica.
 */
public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    Optional<ServiceOffering> findByOrganizationPublicIdAndPublicId(UUID organizationId, UUID publicId);

    Optional<ServiceOffering> findByOrganizationPublicIdAndCode(UUID organizationId, String code);

    Page<ServiceOffering> findByOrganizationPublicId(UUID organizationId, Pageable pageable);

    Page<ServiceOffering> findByOrganizationPublicIdAndActive(UUID organizationId, boolean active, Pageable pageable);

    Page<ServiceOffering> findByOrganizationPublicIdAndActiveAndSpecialtyCode(UUID organizationId, boolean active,
            String specialtyCode, Pageable pageable);

    Page<ServiceOffering> findByOrganizationPublicIdAndSpecialtyCode(UUID organizationId, String specialtyCode,
            Pageable pageable);

    boolean existsByOrganizationPublicIdAndCode(UUID organizationId, String code);
}
