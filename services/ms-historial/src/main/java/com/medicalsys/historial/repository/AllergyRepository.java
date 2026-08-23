package com.medicalsys.historial.repository;

import com.medicalsys.historial.domain.enums.AllergyType;
import com.medicalsys.historial.domain.model.Allergy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a alergias, siempre a traves de su expediente. */
public interface AllergyRepository extends JpaRepository<Allergy, Long> {

    List<Allergy> findByMedicalRecordIdOrderByRecordedAtDesc(Long medicalRecordId);

    boolean existsByMedicalRecordIdAndTypeAndSubstanceIgnoreCase(Long medicalRecordId, AllergyType type,
            String substance);

    Optional<Allergy> findByIdAndMedicalRecordOrganizationPublicId(Long id, UUID organizationPublicId);
}
