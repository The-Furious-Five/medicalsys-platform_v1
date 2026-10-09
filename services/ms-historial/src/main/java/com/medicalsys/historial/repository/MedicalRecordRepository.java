package com.medicalsys.historial.repository;

import com.medicalsys.historial.domain.model.MedicalRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a expedientes.
 *
 * <p>Toda consulta esta acotada por {@code organizationPublicId}: un expediente de otra
 * organizacion no debe ser alcanzable ni siquiera por identificador.
 */
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    Optional<MedicalRecord> findByOrganizationPublicIdAndPublicId(UUID organizationPublicId, UUID publicId);

    /**
     * Sin filtro de organizacion: solo la usa el fallback de lectura cruzada entre tenants, despues
     * de comprobar el permiso via {@code PatientAccessClient}. Nunca se expone directo a un
     * controlador.
     */
    Optional<MedicalRecord> findByPublicId(UUID publicId);

    Optional<MedicalRecord> findByOrganizationPublicIdAndPatientPublicId(UUID organizationPublicId,
            UUID patientPublicId);

    Page<MedicalRecord> findByOrganizationPublicId(UUID organizationPublicId, Pageable pageable);

    /**
     * Devuelve el mayor correlativo emitido por la organizacion para un prefijo.
     *
     * <p>El correlativo usa relleno con ceros de ancho fijo, por lo que el maximo lexicografico
     * coincide con el maximo numerico.
     */
    @Query("""
            select max(r.recordNumber)
            from MedicalRecord r
            where r.organizationPublicId = :organizationPublicId
              and r.recordNumber like concat(:prefix, '%')
            """)
    Optional<String> findMaxRecordNumber(@Param("organizationPublicId") UUID organizationPublicId,
            @Param("prefix") String prefix);
}
