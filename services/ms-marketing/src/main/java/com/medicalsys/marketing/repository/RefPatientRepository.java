package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.model.RefPatient;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la replica demografica de pacientes.
 *
 * <p>No hay ningun metodo que devuelva dato clinico porque no hay ninguna columna clinica que
 * devolver: la restriccion esta en el esquema, no en la interfaz.
 */
public interface RefPatientRepository extends JpaRepository<RefPatient, UUID> {

    List<RefPatient> findByPublicIdIn(List<UUID> publicIds);

    Page<RefPatient> findByMarketingOptOut(boolean marketingOptOut, Pageable pageable);
}
