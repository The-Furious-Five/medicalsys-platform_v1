package com.medicalsys.pacientes.repository;

import com.medicalsys.pacientes.domain.enums.DocumentType;
import com.medicalsys.pacientes.domain.model.Patient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Acceso al paciente.
 *
 * <p>Toda busqueda externa entra por {@code publicId}: el {@code id} numerico no sale del servicio.
 */

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPublicId(UUID publicId);

    boolean existsByDocumentTypeAndDocumentNumberAndDocumentComplement(DocumentType documentType,
            String documentNumber, String documentComplement);

    Optional<Patient> findByDocumentTypeAndDocumentNumberAndDocumentComplement(DocumentType documentType,
            String documentNumber, String documentComplement);

    boolean existsByUserAccountPublicId(UUID userAccountPublicId);

    boolean existsByUserAccountPublicIdAndPublicIdNot(UUID userAccountPublicId, UUID publicId);

    /** Busqueda simple por nombre completo o documento, para la pantalla de recepcion. */
    @Query("""
            SELECT p FROM Patient p
            WHERE (:term IS NULL OR :term = ''
                   OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR UPPER(p.documentNumber) LIKE UPPER(CONCAT('%', :term, '%')))
            """)
    Page<Patient> search(String term, Pageable pageable);
}
