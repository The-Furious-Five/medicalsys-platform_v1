package com.medicalsys.historial.repository;

import com.medicalsys.historial.domain.model.VitalSign;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a signos vitales del encuentro. */
public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {

    List<VitalSign> findByClinicalEncounterIdOrderByMeasuredAtDesc(Long clinicalEncounterId);
}
