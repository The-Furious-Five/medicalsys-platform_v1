package com.medicalsys.catalogos.specialty;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SpecialtyRepository extends JpaRepository<Specialty, Integer> {
    List<Specialty>    findByActiveTrue();
    Optional<Specialty> findByCode(String code);
}
