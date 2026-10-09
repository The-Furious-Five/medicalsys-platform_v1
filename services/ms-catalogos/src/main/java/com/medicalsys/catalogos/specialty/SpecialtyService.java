package com.medicalsys.catalogos.specialty;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SpecialtyService {

    private final SpecialtyRepository repository;

    public SpecialtyService(SpecialtyRepository repository) {
        this.repository = repository;
    }

    public List<SpecialtyDto> findAllActive() {
        return repository.findByActiveTrue()
                .stream()
                .map(SpecialtyDto::from)
                .toList();
    }

    public SpecialtyDto findByCode(String code) {
        return repository.findByCode(code)
                .filter(Specialty::isActive)
                .map(SpecialtyDto::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "SPECIALTY_NOT_FOUND"));
    }
}
