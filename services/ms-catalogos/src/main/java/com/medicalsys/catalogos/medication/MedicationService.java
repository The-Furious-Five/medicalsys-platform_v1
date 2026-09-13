package com.medicalsys.catalogos.medication;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MedicationService {

    private final MedicationRepository repository;

    public MedicationService(MedicationRepository repository) {
        this.repository = repository;
    }

    public List<MedicationDto> findAllActive() {
        return repository.findByActiveTrue().stream().map(MedicationDto::from).toList();
    }

    public MedicationDto findByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }
        return repository.findByCodeIgnoreCase(code.trim())
                .filter(Medication::isActive)
                .map(MedicationDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEDICATION_NOT_FOUND"));
    }
}
