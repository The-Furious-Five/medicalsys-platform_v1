package com.medicalsys.facturacion.repository;

import com.medicalsys.facturacion.domain.enums.PointOfSaleStatus;
import com.medicalsys.facturacion.domain.model.PointOfSale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a puntos de venta.
 *
 * <p>Toda consulta esta acotada por {@code organizationPublicId}: la caja de otra clinica no debe
 * ser alcanzable ni siquiera por codigo.
 */
public interface PointOfSaleRepository extends JpaRepository<PointOfSale, Long> {

    Optional<PointOfSale> findByOrganizationPublicIdAndTaxBranchCodeAndPointOfSaleCode(UUID organizationPublicId,
            Integer taxBranchCode, Integer pointOfSaleCode);

    List<PointOfSale> findByOrganizationPublicIdAndStatusOrderByPointOfSaleCodeAsc(UUID organizationPublicId,
            PointOfSaleStatus status);
}
