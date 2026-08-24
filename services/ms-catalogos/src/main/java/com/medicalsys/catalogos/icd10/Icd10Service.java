package com.medicalsys.catalogos.icd10;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class Icd10Service {

    private final Icd10Repository repository;

    public Icd10Service(Icd10Repository repository) {
        this.repository = repository;
    }

    /** Búsqueda por descripción o prefijo de código (máx 20 resultados). */
    public List<Icd10Dto> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // Intenta primero por código, luego por descripción
        List<Icd10Catalog> byCode = repository
                .findTop20ByActiveTrueAndCodeStartingWithIgnoreCase(query);
        if (!byCode.isEmpty()) {
            return byCode.stream().map(Icd10Dto::from).toList();
        }
        return repository
                .findTop20ByActiveTrueAndDescriptionContainingIgnoreCase(query)
                .stream()
                .map(Icd10Dto::from)
                .toList();
    }

    public Icd10Dto findByCode(String code) {
        return repository.findById(code)
                .filter(Icd10Catalog::isActive)
                .map(Icd10Dto::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "ICD10_NOT_FOUND"));
    }
}
