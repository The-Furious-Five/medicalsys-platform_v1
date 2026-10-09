package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.model.LoyaltyPointsMovement;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso al libro mayor de puntos. */
public interface LoyaltyPointsMovementRepository extends JpaRepository<LoyaltyPointsMovement, Long> {

    Page<LoyaltyPointsMovement> findByProgramIdAndPatientPublicIdOrderByOccurredAtDescIdDesc(Long programId,
            UUID patientPublicId, Pageable pageable);

    boolean existsBySourceEventPublicId(UUID sourceEventPublicId);

    /**
     * Saldo derivado de la suma de asientos.
     *
     * <p>Se calcula, no se guarda: una columna de saldo puede corregirse a mano y dejar de cuadrar
     * con sus movimientos. {@code coalesce} devuelve 0 para un paciente sin asientos, de modo que el
     * caso "todavia no acumulo nada" no exija un Optional.
     */
    @Query("select coalesce(sum(m.points), 0) from LoyaltyPointsMovement m "
            + "where m.program.id = :programId and m.patientPublicId = :patientId")
    int balanceOf(@Param("programId") Long programId, @Param("patientId") UUID patientId);
}
