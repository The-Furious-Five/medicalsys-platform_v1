package com.medicalsys.historial.repository;

import com.medicalsys.historial.domain.enums.EncounterStatus;
import com.medicalsys.historial.domain.model.ClinicalEncounter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a encuentros clinicos. */
public interface ClinicalEncounterRepository extends JpaRepository<ClinicalEncounter, Long> {

    Optional<ClinicalEncounter> findByOrganizationPublicIdAndPublicId(UUID organizationPublicId, UUID publicId);

    /** Sin filtro de organizacion: solo el fallback de lectura cruzada entre tenants. */
    Optional<ClinicalEncounter> findByPublicId(UUID publicId);

    Optional<ClinicalEncounter> findByOrganizationPublicIdAndAppointmentPublicId(UUID organizationPublicId,
            UUID appointmentPublicId);

    boolean existsByAppointmentPublicId(UUID appointmentPublicId);

    /**
     * Busqueda paginada con filtros opcionales.
     *
     * <p>Un filtro nulo no restringe: evita multiplicar metodos derivados por cada combinacion.
     */
    @Query("""
            select e
            from ClinicalEncounter e
            where e.organizationPublicId = :organizationPublicId
              and (:medicalRecordId is null or e.medicalRecord.id = :medicalRecordId)
              and (:professionalPublicId is null or e.healthProfessionalPublicId = :professionalPublicId)
              and (:status is null or e.status = :status)
              and (:from is null or e.startsAt >= :from)
              and (:to is null or e.startsAt < :to)
            """)
    Page<ClinicalEncounter> search(@Param("organizationPublicId") UUID organizationPublicId,
            @Param("medicalRecordId") Long medicalRecordId,
            @Param("professionalPublicId") UUID professionalPublicId,
            @Param("status") EncounterStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
