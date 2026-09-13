package com.medicalsys.historial.repository;

import com.medicalsys.historial.domain.model.Prescription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a recetas. */
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    Optional<Prescription> findByOrganizationPublicIdAndPublicId(UUID organizationPublicId, UUID publicId);

    List<Prescription> findByClinicalEncounterIdOrderByIssuedAtDesc(Long clinicalEncounterId);

    /** Mayor folio emitido por la organizacion; el relleno con ceros hace valido el maximo lexicografico. */
    @Query("""
            select max(p.folio)
            from Prescription p
            where p.organizationPublicId = :organizationPublicId
              and p.folio like concat(:prefix, '%')
            """)
    Optional<String> findMaxFolio(@Param("organizationPublicId") UUID organizationPublicId,
            @Param("prefix") String prefix);
}
