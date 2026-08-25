package com.medicalsys.tenencia.repository;

import com.medicalsys.tenencia.domain.model.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso al tenant.
 *
 * <p>Toda busqueda entra por {@code publicId}: es el identificador que el gateway deriva del JWT y
 * el unico que el resto de la plataforma conoce. El {@code id} numerico no sale del servicio.
 */
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByPublicId(UUID publicId);

    Optional<Organization> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByNit(String nit);
}
