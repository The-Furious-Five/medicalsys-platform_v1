package com.medicalsys.historial.repository;

import com.medicalsys.historial.domain.enums.BackgroundType;
import com.medicalsys.historial.domain.model.MedicalBackground;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a antecedentes del expediente. */
public interface MedicalBackgroundRepository extends JpaRepository<MedicalBackground, Long> {

    List<MedicalBackground> findByMedicalRecordIdOrderByRecordedAtDesc(Long medicalRecordId);

    List<MedicalBackground> findByMedicalRecordIdAndTypeOrderByRecordedAtDesc(Long medicalRecordId,
            BackgroundType type);

    Optional<MedicalBackground> findByIdAndMedicalRecordOrganizationPublicId(Long id, UUID organizationPublicId);
}
