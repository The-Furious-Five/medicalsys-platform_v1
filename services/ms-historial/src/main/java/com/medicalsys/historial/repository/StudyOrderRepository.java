package com.medicalsys.historial.repository;

import com.medicalsys.historial.domain.enums.StudyOrderStatus;
import com.medicalsys.historial.domain.model.StudyOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a ordenes de estudio. */
public interface StudyOrderRepository extends JpaRepository<StudyOrder, Long> {

    Optional<StudyOrder> findByOrganizationPublicIdAndPublicId(UUID organizationPublicId, UUID publicId);

    /** Sin filtro de organizacion: solo el fallback de lectura cruzada entre tenants. */
    Optional<StudyOrder> findByPublicId(UUID publicId);

    List<StudyOrder> findByClinicalEncounterIdOrderByRequestedAtDesc(Long clinicalEncounterId);

    /** Busqueda paginada por estado, tipo de estudio y paciente del expediente asociado. */
    @Query("""
            select o
            from StudyOrder o
            where o.organizationPublicId = :organizationPublicId
              and (:status is null or o.status = :status)
              and (:studyTypeCode is null or o.studyTypeCode = :studyTypeCode)
              and (:patientPublicId is null or o.clinicalEncounter.medicalRecord.patientPublicId = :patientPublicId)
            """)
    Page<StudyOrder> search(@Param("organizationPublicId") UUID organizationPublicId,
            @Param("status") StudyOrderStatus status,
            @Param("studyTypeCode") String studyTypeCode,
            @Param("patientPublicId") UUID patientPublicId,
            Pageable pageable);
}
