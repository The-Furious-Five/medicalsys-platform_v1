package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.model.PatientActivity;
import com.medicalsys.marketing.domain.model.PatientActivityId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la proyeccion comercial por paciente.
 *
 * <p>Toda consulta esta acotada por organizacion: la relacion comercial de una clinica con un
 * paciente no debe ser alcanzable desde otra, ni siquiera por codigo.
 */
public interface PatientActivityRepository extends JpaRepository<PatientActivity, PatientActivityId> {

    Optional<PatientActivity> findByIdPatientPublicIdAndIdOrganizationPublicId(UUID patientPublicId,
            UUID organizationPublicId);

    Page<PatientActivity> findByIdOrganizationPublicId(UUID organizationPublicId, Pageable pageable);

    /** Pacientes con control sugerido vencido: la consulta que alimenta la campana de recordatorio. */
    List<PatientActivity> findByIdOrganizationPublicIdAndSuggestedNextVisitOnLessThanEqual(
            UUID organizationPublicId, LocalDate dueOn);
}
