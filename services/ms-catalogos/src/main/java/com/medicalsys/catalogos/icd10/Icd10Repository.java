package com.medicalsys.catalogos.icd10;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Icd10Repository extends JpaRepository<Icd10Catalog, String> {
    List<Icd10Catalog> findTop20ByActiveTrueAndDescriptionContainingIgnoreCase(String query);
    List<Icd10Catalog> findTop20ByActiveTrueAndCodeStartingWithIgnoreCase(String prefix);
}
