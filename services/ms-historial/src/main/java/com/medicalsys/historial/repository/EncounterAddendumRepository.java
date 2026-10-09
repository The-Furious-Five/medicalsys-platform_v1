package com.medicalsys.historial.repository;

import com.medicalsys.historial.domain.model.EncounterAddendum;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a addendums; solo admite lectura y alta. */
public interface EncounterAddendumRepository extends JpaRepository<EncounterAddendum, Long> {

    List<EncounterAddendum> findByClinicalEncounterIdOrderByCreatedAtAsc(Long clinicalEncounterId);
}
