package com.medicalsys.pacientes.repository;

import com.medicalsys.pacientes.domain.model.PatientMerge;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientMergeRepository extends JpaRepository<PatientMerge, Long> {

    boolean existsByMergedPatient_PublicId(UUID patientId);

    Optional<PatientMerge> findByEventPublicId(UUID eventPublicId);

    List<PatientMerge> findAllBySurvivingPatient_PublicIdOrderByMergedAtDesc(UUID survivingPatientId);
}
