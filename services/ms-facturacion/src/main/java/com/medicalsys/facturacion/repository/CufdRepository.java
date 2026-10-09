package com.medicalsys.facturacion.repository;

import com.medicalsys.facturacion.domain.model.Cufd;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso al CUFD vigente de un punto de venta. */
public interface CufdRepository extends JpaRepository<Cufd, Long> {

    /**
     * Devuelve el CUFD vigente en el instante indicado.
     *
     * <p>Si hubiera mas de uno por un solape de ventanas, gana el ultimo emitido: es el que un
     * documento nuevo debe referenciar.
     */
    @Query("""
            select c
            from Cufd c
            where c.pointOfSale.id = :pointOfSaleId
              and c.validFrom <= :instant
              and c.validUntil > :instant
            order by c.id desc
            limit 1
            """)
    Optional<Cufd> findValidAt(@Param("pointOfSaleId") Long pointOfSaleId, @Param("instant") Instant instant);
}
