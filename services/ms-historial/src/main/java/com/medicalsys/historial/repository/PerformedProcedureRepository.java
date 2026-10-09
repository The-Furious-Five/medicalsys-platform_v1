package com.medicalsys.historial.repository;

import com.medicalsys.historial.domain.model.PerformedProcedure;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a procedimientos realizados en el encuentro. */
public interface PerformedProcedureRepository extends JpaRepository<PerformedProcedure, Long> {

    List<PerformedProcedure> findByClinicalEncounterIdOrderByStartsAtAsc(Long clinicalEncounterId);
}
