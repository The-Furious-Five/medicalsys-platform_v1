package com.medicalsys.pacientes.repository;

import com.medicalsys.pacientes.domain.enums.AccessGrantStatus;
import com.medicalsys.pacientes.domain.model.Patient;
import com.medicalsys.pacientes.domain.model.PatientAccessGrant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientAccessGrantRepository extends JpaRepository<PatientAccessGrant, Long> {

    Optional<PatientAccessGrant> findByPublicIdAndPatient(UUID publicId, Patient patient);

    Optional<PatientAccessGrant> findFirstByPatientAndRequestingOrganizationPublicIdOrderByRequestedAtDesc(
            Patient patient, UUID requestingOrganizationPublicId);

    boolean existsByPatientAndRequestingOrganizationPublicIdAndStatus(
            Patient patient, UUID requestingOrganizationPublicId, AccessGrantStatus status);

    List<PatientAccessGrant> findAllByPatientOrderByRequestedAtDesc(Patient patient);

    List<PatientAccessGrant> findAllByPatientAndRequestingOrganizationPublicIdOrderByRequestedAtDesc(
            Patient patient, UUID requestingOrganizationPublicId);
}
