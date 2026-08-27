package com.medicalsys.pacientes.repository;

import com.medicalsys.pacientes.domain.DocumentType;
import com.medicalsys.pacientes.domain.Patient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPublicId(UUID publicId);

    Optional<Patient> findByDocumentTypeAndDocumentNumberAndDocumentComplement(
            DocumentType documentType, String documentNumber, String documentComplement);

    boolean existsByDocumentTypeAndDocumentNumberAndDocumentComplement(
            DocumentType documentType, String documentNumber, String documentComplement);
}
