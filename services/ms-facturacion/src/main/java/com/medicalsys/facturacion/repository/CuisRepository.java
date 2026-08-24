package com.medicalsys.facturacion.repository;

import com.medicalsys.facturacion.domain.enums.CuisStatus;
import com.medicalsys.facturacion.domain.model.Cuis;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso al CUIS vigente de un punto de venta. */
public interface CuisRepository extends JpaRepository<Cuis, Long> {

    /** Devuelve el CUIS activo que sigue vigente en la fecha indicada. */
    @Query("""
            select c
            from Cuis c
            where c.pointOfSale.id = :pointOfSaleId
              and c.status = :status
              and c.validUntil >= :date
            order by c.id desc
            limit 1
            """)
    Optional<Cuis> findActiveOn(@Param("pointOfSaleId") Long pointOfSaleId, @Param("date") LocalDate date,
            @Param("status") CuisStatus status);

    /** Sobrecarga de conveniencia: el estado siempre es {@code ACTIVE}. */
    default Optional<Cuis> findActiveOn(Long pointOfSaleId, LocalDate date) {
        return findActiveOn(pointOfSaleId, date, CuisStatus.ACTIVE);
    }
}
