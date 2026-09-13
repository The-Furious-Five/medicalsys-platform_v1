package com.medicalsys.historial.repository;

import com.medicalsys.historial.domain.model.Diagnosis;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a diagnosticos del encuentro. */
public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

    List<Diagnosis> findByClinicalEncounterIdOrderByPrimaryDescIdAsc(Long clinicalEncounterId);

    boolean existsByClinicalEncounterIdAndIcd10Code(Long clinicalEncounterId, String icd10Code);

    List<Diagnosis> findByClinicalEncounterIdAndPrimaryTrue(Long clinicalEncounterId);

    Optional<Diagnosis> findByIdAndClinicalEncounterOrganizationPublicId(Long id, UUID organizationPublicId);
}
