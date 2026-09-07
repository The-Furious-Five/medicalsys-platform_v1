package com.medicalsys.catalogos.medication;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    List<Medication> findByActiveTrue();

    Optional<Medication> findByCodeIgnoreCase(String code);
}
